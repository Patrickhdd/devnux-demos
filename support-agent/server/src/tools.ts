import type Anthropic from "@anthropic-ai/sdk";
import { z } from "zod";
import type { Db } from "./db.js";
import { reference, withTransaction } from "./db.js";

/**
 * Everything a tool may touch. The customer comes from the signed session, never
 * from model input, so a tool can only ever read or change that customer's data.
 */
export interface ToolContext {
  db: Db;
  customerId: number;
  conversationId: string;
  now: () => Date;
}

/** A tool result the model sees, plus a one-line summary for the customer and staff UI. */
export interface ToolOutcome {
  result: unknown;
  summary: string;
  isError?: boolean;
}

interface ToolSpec<S extends z.ZodType> {
  definition: Anthropic.Beta.BetaTool;
  input: S;
  run: (ctx: ToolContext, input: z.infer<S>) => Promise<ToolOutcome>;
}

const RETURN_WINDOW_DAYS = 30;
const DAY = 24 * 60 * 60 * 1000;
const dollars = (cents: number) => `$${(cents / 100).toFixed(2)}`;
const orderNumber = z.string().trim().toUpperCase().regex(/^HP-\d{5}$/, "Order numbers look like HP-12345");

function tool<S extends z.ZodType>(spec: ToolSpec<S>): ToolSpec<S> {
  return spec;
}

async function findOrder(ctx: ToolContext, number: string) {
  const { rows } = await ctx.db.query(
    `SELECT o.*, coalesce(json_agg(json_build_object('name', p.name, 'sku', p.sku, 'quantity', i.quantity, 'price_cents', i.price_cents)
              ORDER BY i.id) FILTER (WHERE i.id IS NOT NULL), '[]') AS items
       FROM shop_order o
       LEFT JOIN order_item i ON i.order_id = o.id
       LEFT JOIN product p ON p.id = i.product_id
      WHERE o.order_number = $1 AND o.customer_id = $2
      GROUP BY o.id`,
    [number, ctx.customerId],
  );
  return rows[0] as
    | { id: number; order_number: string; status: string; placed_at: Date; shipped_at: Date | null; delivered_at: Date | null;
        carrier: string | null; tracking_code: string | null; ship_to: string;
        items: Array<{ name: string; sku: string; quantity: number; price_cents: number }> }
    | undefined;
}

const notFound = (number: string): ToolOutcome => ({
  result: { error: "not_found", message: `No order ${number} on this customer's account.` },
  summary: `Looked for order ${number} (not found)`,
  isError: true,
});

export const tools = [
  tool({
    definition: {
      name: "search_help_center",
      description:
        "Search Harbor & Pine's help center articles (returns, shipping, tracking, cancellations, warranty, damaged items, sizing). " +
        "Use it before answering any policy question, and base the answer on the articles returned.",
      eager_input_streaming: true,
      input_schema: {
        type: "object",
        properties: { query: { type: "string", description: "A few keywords, for example 'return window' or 'expedited shipping cost'." } },
        required: ["query"],
      },
    },
    input: z.object({ query: z.string().trim().min(2).max(200) }),
    async run(ctx, { query }) {
      const { rows } = await ctx.db.query(
        `SELECT slug, title, body, ts_rank(search, q) AS rank
           FROM help_article, websearch_to_tsquery('english', $1) q
          WHERE search @@ q
          ORDER BY rank DESC LIMIT 3`,
        [query],
      );
      let articles = rows;
      if (articles.length === 0) {
        // Fall back to OR-matching the words so short questions still find something.
        const words = query.split(/\s+/).filter((w) => w.length > 2).join(" or ");
        articles = (await ctx.db.query(
          `SELECT slug, title, body FROM help_article, websearch_to_tsquery('english', $1) q
            WHERE search @@ q ORDER BY ts_rank(search, q) DESC LIMIT 3`, [words || query])).rows;
      }
      return {
        result: { articles: articles.map(({ slug, title, body }) => ({ slug, title, body })) },
        summary: articles.length ? `Searched the help center for "${query}"` : `Searched the help center for "${query}" (no match)`,
      };
    },
  }),

  tool({
    definition: {
      name: "list_orders",
      description: "List the signed-in customer's recent orders with their status. Use it when the customer doesn't give an order number.",
      eager_input_streaming: true,
      input_schema: { type: "object", properties: {} },
    },
    input: z.object({}).passthrough(),
    async run(ctx) {
      const { rows } = await ctx.db.query(
        `SELECT order_number, status, placed_at, delivered_at,
                (SELECT sum(quantity * price_cents) FROM order_item WHERE order_id = o.id)::int AS total_cents,
                (SELECT string_agg(p.name, ', ') FROM order_item i JOIN product p ON p.id = i.product_id WHERE i.order_id = o.id) AS items
           FROM shop_order o WHERE customer_id = $1 ORDER BY placed_at DESC LIMIT 10`,
        [ctx.customerId],
      );
      return {
        result: { orders: rows.map((r) => ({ ...r, total: dollars(r.total_cents) })) },
        summary: `Looked up the customer's orders (${rows.length})`,
      };
    },
  }),

  tool({
    definition: {
      name: "get_order",
      description: "Get one order's items, status, shipping address, carrier and tracking code, and whether it can still be returned or cancelled.",
      eager_input_streaming: true,
      input_schema: {
        type: "object",
        properties: { order_number: { type: "string", description: "For example HP-10482." } },
        required: ["order_number"],
      },
    },
    input: z.object({ order_number: orderNumber }),
    async run(ctx, { order_number }) {
      const o = await findOrder(ctx, order_number);
      if (!o) return notFound(order_number);
      const now = ctx.now().getTime();
      const daysSinceDelivery = o.delivered_at ? Math.floor((now - o.delivered_at.getTime()) / DAY) : null;
      const total = o.items.reduce((s, i) => s + i.quantity * i.price_cents, 0);
      return {
        result: {
          order_number: o.order_number, status: o.status, placed_at: o.placed_at, shipped_at: o.shipped_at,
          delivered_at: o.delivered_at, carrier: o.carrier, tracking_code: o.tracking_code, ship_to: o.ship_to,
          items: o.items.map((i) => ({ ...i, price: dollars(i.price_cents) })),
          total: dollars(total),
          can_cancel: o.status === "processing",
          can_request_refund: o.status === "delivered" && daysSinceDelivery !== null && daysSinceDelivery <= RETURN_WINDOW_DAYS,
          days_since_delivery: daysSinceDelivery,
          return_window_days: RETURN_WINDOW_DAYS,
        },
        summary: `Opened order ${o.order_number}`,
      };
    },
  }),

  tool({
    definition: {
      name: "request_refund",
      description:
        "Submit a refund request for a delivered order that is inside the 30-day return window. A staff member reviews every request " +
        "before money moves, so tell the customer it is pending review, never that it is approved. Only call this after the customer " +
        "has clearly asked for a refund or return of that order.",
      eager_input_streaming: true,
      input_schema: {
        type: "object",
        properties: {
          order_number: { type: "string" },
          reason: { type: "string", description: "The customer's reason, in a short sentence." },
        },
        required: ["order_number", "reason"],
      },
    },
    input: z.object({ order_number: orderNumber, reason: z.string().trim().min(3).max(500) }),
    async run(ctx, { order_number, reason }) {
      const o = await findOrder(ctx, order_number);
      if (!o) return notFound(order_number);
      const refuse = (code: string, message: string): ToolOutcome => ({
        result: { error: code, message }, summary: `Refund for ${o.order_number} not allowed (${code.replaceAll("_", " ")})`, isError: true,
      });
      if (o.status !== "delivered" || !o.delivered_at) {
        return refuse("not_delivered", "Only delivered orders can be refunded. Processing orders can be cancelled instead.");
      }
      const days = Math.floor((ctx.now().getTime() - o.delivered_at.getTime()) / DAY);
      if (days > RETURN_WINDOW_DAYS) {
        return refuse("outside_return_window",
          `Delivered ${days} days ago, past the ${RETURN_WINDOW_DAYS}-day window. Offer to escalate to the support team for an exception.`);
      }
      const amount = o.items.reduce((s, i) => s + i.quantity * i.price_cents, 0);
      try {
        const ref = reference("RF");
        await ctx.db.query(
          `INSERT INTO refund_request (reference, order_id, conversation_id, amount_cents, reason) VALUES ($1, $2, $3, $4, $5)`,
          [ref, o.id, ctx.conversationId, amount, reason]);
        return {
          result: { reference: ref, status: "pending_review", amount: dollars(amount),
            next_steps: "A team member reviews it within one business day, then we email a prepaid return label." },
          summary: `Submitted refund request ${ref} for ${o.order_number} (${dollars(amount)}), pending staff review`,
        };
      } catch (err) {
        if ((err as { code?: string }).code === "23505") {
          return refuse("already_requested", "A refund for this order has already been requested.");
        }
        throw err;
      }
    },
  }),

  tool({
    definition: {
      name: "cancel_order",
      description:
        "Cancel an order that is still processing. This cannot be undone. Before calling it, tell the customer what will be cancelled " +
        "and get an explicit yes in their latest message, then pass customer_confirmed: true.",
      eager_input_streaming: true,
      input_schema: {
        type: "object",
        properties: {
          order_number: { type: "string" },
          customer_confirmed: { type: "boolean", description: "True only if the customer explicitly confirmed the cancellation." },
        },
        required: ["order_number", "customer_confirmed"],
      },
    },
    input: z.object({ order_number: orderNumber, customer_confirmed: z.boolean() }),
    async run(ctx, { order_number, customer_confirmed }) {
      if (!customer_confirmed) {
        return { result: { error: "confirmation_required", message: "Ask the customer to confirm first." },
          summary: `Asked to confirm before cancelling ${order_number}`, isError: true };
      }
      const updated = await withTransaction(ctx.db, async (c) => {
        const r = await c.query(
          `UPDATE shop_order SET status = 'cancelled'
            WHERE order_number = $1 AND customer_id = $2 AND status = 'processing' RETURNING order_number`,
          [order_number, ctx.customerId]);
        return r.rowCount === 1;
      });
      if (!updated) {
        const o = await findOrder(ctx, order_number);
        if (!o) return notFound(order_number);
        return { result: { error: "cannot_cancel", status: o.status, message: "Only processing orders can be cancelled." },
          summary: `Could not cancel ${order_number} (already ${o.status})`, isError: true };
      }
      return { result: { order_number, status: "cancelled", refund: "The payment authorization is released within 3 business days." },
        summary: `Cancelled order ${order_number}` };
    },
  }),

  tool({
    definition: {
      name: "escalate_to_human",
      description:
        "Hand the conversation to the human support team. Use it when the customer asks for a person, when a request is outside policy " +
        "(for example a late return), for warranty claims, or when you can't resolve the issue with the other tools.",
      eager_input_streaming: true,
      input_schema: {
        type: "object",
        properties: {
          summary: { type: "string", description: "What the customer needs and what you already checked, in 1 to 3 sentences." },
          priority: { type: "string", enum: ["low", "normal", "urgent"] },
        },
        required: ["summary", "priority"],
      },
    },
    input: z.object({ summary: z.string().trim().min(10).max(1000), priority: z.enum(["low", "normal", "urgent"]) }),
    async run(ctx, { summary, priority }) {
      const ref = reference("TK");
      await withTransaction(ctx.db, async (c) => {
        await c.query(
          `INSERT INTO support_ticket (reference, conversation_id, customer_id, priority, summary) VALUES ($1, $2, $3, $4, $5)`,
          [ref, ctx.conversationId, ctx.customerId, priority, summary]);
        await c.query(`UPDATE conversation SET status = 'escalated' WHERE id = $1`, [ctx.conversationId]);
      });
      return {
        result: { reference: ref, priority, expected_reply: priority === "urgent" ? "within 2 hours" : "within one business day" },
        summary: `Escalated to the support team (ticket ${ref}, ${priority})`,
      };
    },
  }),
];

export const toolDefinitions: Anthropic.Beta.BetaTool[] = tools.map((t) => t.definition);

/** Validates the model's input against the tool's schema, then runs it. Never throws for bad input. */
export async function runTool(ctx: ToolContext, name: string, rawInput: unknown): Promise<ToolOutcome> {
  const spec = tools.find((t) => t.definition.name === name);
  if (!spec) return { result: { error: "unknown_tool", name }, summary: `Unknown tool ${name}`, isError: true };
  const parsed = spec.input.safeParse(rawInput ?? {});
  if (!parsed.success) {
    return {
      result: { error: "invalid_input", issues: parsed.error.issues.map((i) => `${i.path.join(".") || "input"}: ${i.message}`) },
      summary: `Rejected invalid input for ${name}`,
      isError: true,
    };
  }
  // The cast is safe: parsed.data was produced by this spec's own schema.
  return (spec.run as (c: ToolContext, i: unknown) => Promise<ToolOutcome>)(ctx, parsed.data);
}
