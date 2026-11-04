import Fastify, { type FastifyReply, type FastifyRequest } from "fastify";
import { createHmac, timingSafeEqual } from "node:crypto";
import { z } from "zod";
import type { Config } from "./config.js";
import type { Db } from "./db.js";
import { runTurn, type AgentEvent, type Customer, type ModelClient } from "./agent.js";

export interface AppDeps {
  db: Db;
  config: Config;
  /** Undefined when no API key is configured. */
  model?: ModelClient;
  now?: () => Date;
}

class HttpError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

function sign(secret: string, value: string) {
  return createHmac("sha256", secret).update(value).digest("base64url");
}

function safeEqual(a: string, b: string) {
  const x = Buffer.from(a), y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

export function buildApp({ db, config, model, now }: AppDeps) {
  const app = Fastify({ logger: { level: process.env.LOG_LEVEL ?? "info" } });
  const busy = new Set<string>();

  app.setErrorHandler((err, _req, reply) => {
    if (err instanceof HttpError) return reply.status(err.status).send({ error: err.message });
    if (err instanceof z.ZodError) return reply.status(400).send({ error: err.issues.map((i) => i.message).join("; ") });
    const status = (err as { statusCode?: number }).statusCode;
    if (status && status < 500) return reply.status(status).send({ error: (err as Error).message });
    app.log.error(err);
    return reply.status(500).send({ error: "Something went wrong" });
  });

  async function customerFrom(req: FastifyRequest): Promise<Customer> {
    const token = req.headers.authorization?.replace(/^Bearer /, "") ?? "";
    const [id, sig] = token.split(".");
    if (!id || !sig || !safeEqual(sig, sign(config.sessionSecret, `customer:${id}`))) throw new HttpError(401, "Please sign in");
    const { rows } = await db.query("SELECT id, full_name, email FROM customer WHERE id = $1", [Number(id)]);
    if (!rows[0]) throw new HttpError(401, "Please sign in");
    return { id: rows[0].id, fullName: rows[0].full_name, email: rows[0].email };
  }

  function requireStaff(req: FastifyRequest) {
    const key = String(req.headers["x-staff-key"] ?? "");
    if (!safeEqual(key, config.staffKey)) throw new HttpError(401, "Staff key required");
  }

  async function ownConversation(customer: Customer, id: string) {
    if (!z.uuid().safeParse(id).success) throw new HttpError(404, "Conversation not found");
    const { rows } = await db.query("SELECT id, status FROM conversation WHERE id = $1 AND customer_id = $2", [id, customer.id]);
    if (!rows[0]) throw new HttpError(404, "Conversation not found");
    return rows[0] as { id: string; status: string };
  }

  app.get("/api/health", async () => ({ status: "ok", agentEnabled: Boolean(model), model: config.model }));

  // ---- Demo sign-in. A real store would use its existing login; this keeps the demo one click. ----
  app.get("/api/demo/customers", async () =>
    (await db.query("SELECT id, full_name AS \"fullName\", email FROM customer ORDER BY id")).rows);

  app.post("/api/session", async (req) => {
    const { customerId } = z.object({ customerId: z.number().int().positive() }).parse(req.body);
    const { rows } = await db.query("SELECT id, full_name, email FROM customer WHERE id = $1", [customerId]);
    if (!rows[0]) throw new HttpError(404, "Customer not found");
    return { token: `${customerId}.${sign(config.sessionSecret, `customer:${customerId}`)}`,
      customer: { id: rows[0].id, fullName: rows[0].full_name, email: rows[0].email } };
  });

  // ---- Customer account ----
  app.get("/api/me/orders", async (req) => {
    const customer = await customerFrom(req);
    return (await db.query(`
      SELECT o.order_number AS "orderNumber", o.status, o.placed_at AS "placedAt", o.delivered_at AS "deliveredAt",
             (SELECT string_agg(p.name, ', ' ORDER BY i.id) FROM order_item i JOIN product p ON p.id = i.product_id WHERE i.order_id = o.id) AS items,
             (SELECT sum(quantity * price_cents) FROM order_item WHERE order_id = o.id)::int AS "totalCents"
        FROM shop_order o WHERE o.customer_id = $1 ORDER BY o.placed_at DESC`, [customer.id])).rows;
  });

  // ---- Customer chat ----
  app.post("/api/conversations", async (req, reply) => {
    const customer = await customerFrom(req);
    const { rows } = await db.query("INSERT INTO conversation (customer_id) VALUES ($1) RETURNING id, status", [customer.id]);
    return reply.status(201).send(rows[0]);
  });

  app.get<{ Params: { id: string } }>("/api/conversations/:id", async (req) => {
    const customer = await customerFrom(req);
    const conv = await ownConversation(customer, req.params.id);
    const { rows } = await db.query(
      "SELECT id, kind, payload, created_at AS \"createdAt\" FROM conversation_event WHERE conversation_id = $1 ORDER BY id", [conv.id]);
    return { ...conv, events: rows };
  });

  app.post<{ Params: { id: string } }>("/api/conversations/:id/messages", async (req, reply: FastifyReply) => {
    const customer = await customerFrom(req);
    const conv = await ownConversation(customer, req.params.id);
    const { text } = z.object({ text: z.string().trim().min(1, "Type a message").max(2000, "Messages are limited to 2,000 characters") })
      .parse(req.body);
    if (!model) {
      throw new HttpError(503, "The assistant is not switched on. Set ANTHROPIC_API_KEY on the server to enable it.");
    }
    if (busy.has(conv.id)) throw new HttpError(409, "Please wait for the current reply to finish");
    busy.add(conv.id);

    // Newline-delimited JSON: one AgentEvent per line, flushed as it happens.
    reply.raw.writeHead(200, { "content-type": "application/x-ndjson; charset=utf-8", "cache-control": "no-store", "x-accel-buffering": "no" });
    const emit = (e: AgentEvent) => { if (!reply.raw.writableEnded) reply.raw.write(JSON.stringify(e) + "\n"); };
    try {
      await runTurn({ db, model, config, conversationId: conv.id, customer, text, emit, now });
    } catch (err) {
      req.log.error(err);
      emit({ type: "error", message: "Something went wrong. Please try again." });
    } finally {
      busy.delete(conv.id);
      reply.raw.end();
    }
    return reply;
  });

  // ---- Staff console ----
  app.get("/api/staff/overview", async (req) => {
    requireStaff(req);
    const { rows } = await db.query(`SELECT
        (SELECT count(*) FROM conversation)::int AS conversations,
        (SELECT count(*) FROM refund_request WHERE status = 'pending_review')::int AS "pendingRefunds",
        (SELECT count(*) FROM support_ticket WHERE status = 'open')::int AS "openTickets",
        (SELECT coalesce(sum(input_tokens), 0) FROM conversation)::int AS "inputTokens",
        (SELECT coalesce(sum(output_tokens), 0) FROM conversation)::int AS "outputTokens"`);
    return rows[0];
  });

  app.get("/api/staff/conversations", async (req) => {
    requireStaff(req);
    return (await db.query(`
      SELECT c.id, c.status, c.updated_at AS "updatedAt", cu.full_name AS customer,
             (SELECT payload->>'text' FROM conversation_event e WHERE e.conversation_id = c.id AND kind = 'customer' ORDER BY id LIMIT 1) AS "firstMessage",
             (SELECT count(*) FROM conversation_event e WHERE e.conversation_id = c.id AND kind = 'tool')::int AS "toolCalls"
        FROM conversation c JOIN customer cu ON cu.id = c.customer_id
       WHERE EXISTS (SELECT 1 FROM conversation_event e WHERE e.conversation_id = c.id)
       ORDER BY c.updated_at DESC LIMIT 50`)).rows;
  });

  app.get<{ Params: { id: string } }>("/api/staff/conversations/:id", async (req) => {
    requireStaff(req);
    if (!z.uuid().safeParse(req.params.id).success) throw new HttpError(404, "Conversation not found");
    const conv = (await db.query(
      `SELECT c.id, c.status, c.input_tokens AS "inputTokens", c.output_tokens AS "outputTokens", cu.full_name AS customer, cu.email
         FROM conversation c JOIN customer cu ON cu.id = c.customer_id WHERE c.id = $1`, [req.params.id])).rows[0];
    if (!conv) throw new HttpError(404, "Conversation not found");
    const events = (await db.query(
      `SELECT id, kind, payload, created_at AS "createdAt" FROM conversation_event WHERE conversation_id = $1 ORDER BY id`, [conv.id])).rows;
    return { ...conv, events };
  });

  app.get("/api/staff/refunds", async (req) => {
    requireStaff(req);
    return (await db.query(`
      SELECT r.id, r.reference, r.amount_cents AS "amountCents", r.reason, r.status, r.created_at AS "createdAt",
             r.decided_by AS "decidedBy", r.conversation_id AS "conversationId", o.order_number AS "orderNumber", cu.full_name AS customer
        FROM refund_request r JOIN shop_order o ON o.id = r.order_id JOIN customer cu ON cu.id = o.customer_id
       ORDER BY (r.status = 'pending_review') DESC, r.created_at DESC LIMIT 100`)).rows;
  });

  app.post<{ Params: { id: string } }>("/api/staff/refunds/:id/decision", async (req) => {
    requireStaff(req);
    const { decision, staffName } = z.object({ decision: z.enum(["approved", "denied"]), staffName: z.string().trim().min(1).max(80).default("Support team") })
      .parse(req.body);
    const { rows } = await db.query(
      `UPDATE refund_request SET status = $2, decided_by = $3, decided_at = now()
        WHERE id = $1 AND status = 'pending_review' RETURNING id, reference, status`,
      [Number(req.params.id), decision, staffName]);
    if (!rows[0]) throw new HttpError(409, "This request was already decided");
    return rows[0];
  });

  app.get("/api/staff/tickets", async (req) => {
    requireStaff(req);
    return (await db.query(`
      SELECT t.id, t.reference, t.priority, t.summary, t.status, t.created_at AS "createdAt", t.conversation_id AS "conversationId",
             cu.full_name AS customer, cu.email
        FROM support_ticket t JOIN customer cu ON cu.id = t.customer_id
       ORDER BY (t.status = 'open') DESC, (t.priority = 'urgent') DESC, t.created_at DESC LIMIT 100`)).rows;
  });

  app.post<{ Params: { id: string } }>("/api/staff/tickets/:id/resolve", async (req) => {
    requireStaff(req);
    const { rows } = await db.query(
      "UPDATE support_ticket SET status = 'resolved' WHERE id = $1 AND status = 'open' RETURNING id, reference, status", [Number(req.params.id)]);
    if (!rows[0]) throw new HttpError(409, "This ticket is already resolved");
    return rows[0];
  });

  return app;
}
