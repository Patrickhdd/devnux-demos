import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import type { StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import type { Db } from "../src/db.js";
import { buildApp } from "../src/app.js";
import { loadConfig } from "../src/config.js";
import type { AgentEvent, ModelClient } from "../src/agent.js";
import { lastToolResults, message, NOW, parseResult, scriptedModel, startDatabase, text, toolUse } from "./helpers.js";

let container: StartedPostgreSqlContainer;
let db: Db;
const config = { ...loadConfig({}), staffKey: "test-staff" };

beforeAll(async () => ({ container, db } = await startDatabase()));
afterAll(async () => { await db?.end(); await container?.stop(); });
beforeEach(async () => {
  await db.query("DELETE FROM refund_request; DELETE FROM support_ticket; DELETE FROM conversation;");
  await db.query("UPDATE shop_order SET status = 'processing' WHERE order_number = 'HP-10533'");
});

function appWith(model?: ModelClient) {
  return buildApp({ db, config, model, now: () => NOW });
}

async function signIn(app: ReturnType<typeof appWith>, email: string) {
  const id = (await db.query("SELECT id FROM customer WHERE email = $1", [email])).rows[0].id;
  const res = await app.inject({ method: "POST", url: "/api/session", payload: { customerId: id } });
  return { authorization: `Bearer ${res.json().token}` };
}

async function chat(app: ReturnType<typeof appWith>, headers: Record<string, string>, conversationId: string, textIn: string) {
  const res = await app.inject({ method: "POST", url: `/api/conversations/${conversationId}/messages`, headers, payload: { text: textIn } });
  expect(res.statusCode).toBe(200);
  const events = res.body.trim().split("\n").map((l) => JSON.parse(l) as AgentEvent);
  return { events, done: events.find((e) => e.type === "done") as Extract<AgentEvent, { type: "done" }> };
}

async function newConversation(app: ReturnType<typeof appWith>, headers: Record<string, string>) {
  return (await app.inject({ method: "POST", url: "/api/conversations", headers })).json().id as string;
}

const jordan = "jordan.rivera@example.com";
const taylor = "taylor.brooks@example.com";

describe("support agent", () => {
  it("looks up an order, submits a refund for staff review, and streams the reply", async () => {
    const script = scriptedModel([
      message([text("Let me check that order."), toolUse("get_order", { order_number: "hp-10482" })], "tool_use"),
      (p) => {
        const order = parseResult(lastToolResults(p)[0]);
        expect(order.can_request_refund).toBe(true);
        expect(order.days_since_delivery).toBe(10);
        return message([toolUse("request_refund", { order_number: "HP-10482", reason: "Boots are too small" })], "tool_use");
      },
      (p) => {
        const r = parseResult(lastToolResults(p)[0]);
        expect(r.status).toBe("pending_review");
        expect(r.amount).toBe("$201.00");
        return message([text(`Done. Your refund request ${r.reference} is pending review.`)]);
      },
    ]);
    const app = appWith(script.model);
    const headers = await signIn(app, jordan);
    const id = await newConversation(app, headers);

    const { events, done } = await chat(app, headers, id, "My boots from HP-10482 are too small, can I get a refund?");

    script.assertDone();
    expect(events.filter((e) => e.type === "tool_finished").map((e) => (e as any).name)).toEqual(["get_order", "request_refund"]);
    expect(done.reply).toMatch(/^Let me check that order\.\n\nDone\. Your refund request RF-\w{5} is pending review\.$/);

    const refunds = (await db.query("SELECT status, amount_cents, conversation_id FROM refund_request")).rows;
    expect(refunds).toEqual([{ status: "pending_review", amount_cents: 20100, conversation_id: id }]);

    // Every request carries the tools, the two system blocks, caching, effort and the fallback opt-in.
    const req = script.requests[0];
    expect(req.model).toBe("claude-opus-5-5");
    expect(req.tools?.map((t) => (t as any).name)).toContain("request_refund");
    expect(req.cache_control).toEqual({ type: "ephemeral" });
    expect(req.fallbacks).toBe("default");
    expect(req.betas).toContain("server-side-fallback-2026-07-01");
    expect(JSON.stringify(req.system)).toContain("Jordan Rivera");
  });

  it("only sees the signed-in customer's orders, whatever the model asks for", async () => {
    const script = scriptedModel([
      message([toolUse("get_order", { order_number: "HP-10455" })], "tool_use"), // Taylor's order
      (p) => {
        const [r] = lastToolResults(p);
        expect(r.is_error).toBe(true);
        expect(parseResult(r).error).toBe("not_found");
        return message([text("I couldn't find that order on your account.")]);
      },
    ]);
    const app = appWith(script.model);
    const headers = await signIn(app, jordan);
    await chat(app, headers, await newConversation(app, headers), "What's in order HP-10455?");
    script.assertDone();
  });

  it("refuses refunds outside the 30-day window and duplicate refunds", async () => {
    const script = scriptedModel([
      message([
        toolUse("request_refund", { order_number: "HP-10391", reason: "Changed my mind" }),
        toolUse("request_refund", { order_number: "HP-10482", reason: "Too small" }),
      ], "tool_use"),
      (p) => {
        const [late, ok] = lastToolResults(p);
        expect(parseResult(late).error).toBe("outside_return_window");
        expect(ok.is_error).toBeFalsy();
        return message([toolUse("request_refund", { order_number: "HP-10482", reason: "Again" })], "tool_use");
      },
      (p) => {
        expect(parseResult(lastToolResults(p)[0]).error).toBe("already_requested");
        return message([text("One request is in, the other is past the window.")]);
      },
    ]);
    const app = appWith(script.model);
    const headers = await signIn(app, jordan);
    await chat(app, headers, await newConversation(app, headers), "Refund both orders please");
    script.assertDone();
    expect((await db.query("SELECT count(*)::int AS n FROM refund_request")).rows[0].n).toBe(1);
  });

  it("will not cancel without confirmation, and cancels only processing orders", async () => {
    const script = scriptedModel([
      message([toolUse("cancel_order", { order_number: "HP-10533", customer_confirmed: false })], "tool_use"),
      (p) => {
        expect(parseResult(lastToolResults(p)[0]).error).toBe("confirmation_required");
        return message([toolUse("cancel_order", { order_number: "HP-10533", customer_confirmed: true }),
          toolUse("cancel_order", { order_number: "HP-10517", customer_confirmed: true })], "tool_use");
      },
      (p) => {
        const [ok, shipped] = lastToolResults(p);
        expect(parseResult(ok).status).toBe("cancelled");
        expect(parseResult(shipped).error).toBe("cannot_cancel");
        return message([text("Your tent order is cancelled. The backpack order already shipped.")]);
      },
    ]);
    const app = appWith(script.model);
    const headers = await signIn(app, jordan);
    await chat(app, headers, await newConversation(app, headers), "Yes, cancel them");
    script.assertDone();
    const statuses = (await db.query("SELECT order_number, status FROM shop_order WHERE order_number IN ('HP-10533','HP-10517') ORDER BY 1")).rows;
    expect(statuses).toEqual([{ order_number: "HP-10517", status: "shipped" }, { order_number: "HP-10533", status: "cancelled" }]);
  });

  it("rejects invalid tool input without running the tool", async () => {
    const script = scriptedModel([
      message([toolUse("get_order", { order_number: "10482; DROP TABLE shop_order" })], "tool_use"),
      (p) => {
        const [r] = lastToolResults(p);
        expect(r.is_error).toBe(true);
        expect(parseResult(r).error).toBe("invalid_input");
        return message([text("Could you check the order number? It looks like HP-12345.")]);
      },
    ]);
    const app = appWith(script.model);
    const headers = await signIn(app, jordan);
    await chat(app, headers, await newConversation(app, headers), "order 10482");
    script.assertDone();
  });

  it("searches the help center with Postgres full-text search", async () => {
    const script = scriptedModel([
      message([toolUse("search_help_center", { query: "return window refund" })], "tool_use"),
      (p) => {
        const r = parseResult(lastToolResults(p)[0]);
        expect(r.articles[0].slug).toBe("returns-and-refunds");
        return message([text("You have 30 days from delivery.")]);
      },
    ]);
    const app = appWith(script.model);
    const headers = await signIn(app, taylor);
    await chat(app, headers, await newConversation(app, headers), "How long do I have to return something?");
    script.assertDone();
  });

  it("escalates to a person and marks the conversation", async () => {
    const script = scriptedModel([
      message([toolUse("escalate_to_human", { summary: "Customer wants a warranty repair on a tent zipper.", priority: "normal" })], "tool_use"),
      message([text("I've passed this to our team.")]),
    ]);
    const app = appWith(script.model);
    const headers = await signIn(app, taylor);
    const { done } = await chat(app, headers, await newConversation(app, headers), "My tent zipper broke");
    script.assertDone();
    expect(done.conversationStatus).toBe("escalated");
    const tickets = await app.inject({ url: "/api/staff/tickets", headers: { "x-staff-key": "test-staff" } });
    expect(tickets.json()[0]).toMatchObject({ priority: "normal", customer: "Taylor Brooks", status: "open" });
  });

  it("keeps the transcript append-only across turns", async () => {
    const script = scriptedModel([
      message([{ type: "thinking", thinking: "", signature: "sig-1" } as any, text("Hi Jordan! How can I help?")]),
      message([text("Sure.")]),
    ]);
    const app = appWith(script.model);
    const headers = await signIn(app, jordan);
    const id = await newConversation(app, headers);
    await chat(app, headers, id, "Hello");
    await chat(app, headers, id, "Thanks");
    script.assertDone();

    const [first, second] = script.requests;
    // The second request starts with exactly what the first sent, then the reply as returned (thinking included).
    expect(second.messages.slice(0, first.messages.length)).toEqual(first.messages);
    expect(second.messages[1]).toMatchObject({ role: "assistant", content: [{ type: "thinking", signature: "sig-1" }, { type: "text" }] });
    expect(second.messages).toHaveLength(3);
  });

  it("stops after the turn limit instead of looping forever", async () => {
    const loop = Array.from({ length: config.maxTurns }, () => message([toolUse("list_orders", {})], "tool_use"));
    const script = scriptedModel(loop);
    const app = appWith(script.model);
    const headers = await signIn(app, jordan);
    const { done } = await chat(app, headers, await newConversation(app, headers), "list everything forever");
    script.assertDone();
    expect(done.reply).toMatch(/more steps than I can handle/);
  });
});

describe("access control", () => {
  it("requires a session and keeps conversations private", async () => {
    const app = appWith(scriptedModel([]).model);
    expect((await app.inject({ method: "POST", url: "/api/conversations" })).statusCode).toBe(401);
    expect((await app.inject({ method: "POST", url: "/api/conversations", headers: { authorization: "Bearer 1.forged" } })).statusCode).toBe(401);

    const jordanHeaders = await signIn(app, jordan);
    const id = await newConversation(app, jordanHeaders);
    const taylorHeaders = await signIn(app, taylor);
    expect((await app.inject({ url: `/api/conversations/${id}`, headers: taylorHeaders })).statusCode).toBe(404);
    expect((await app.inject({ url: `/api/conversations/${id}`, headers: jordanHeaders })).statusCode).toBe(200);
  });

  it("requires the staff key, and decides a refund only once", async () => {
    const script = scriptedModel([
      message([toolUse("request_refund", { order_number: "HP-10482", reason: "Too small" })], "tool_use"),
      message([text("Submitted.")]),
    ]);
    const app = appWith(script.model);
    const headers = await signIn(app, jordan);
    await chat(app, headers, await newConversation(app, headers), "refund HP-10482");
    script.assertDone();

    expect((await app.inject({ url: "/api/staff/refunds" })).statusCode).toBe(401);
    expect((await app.inject({ url: "/api/staff/refunds", headers: { "x-staff-key": "wrong" } })).statusCode).toBe(401);
    const staff = { "x-staff-key": "test-staff" };
    const [refund] = (await app.inject({ url: "/api/staff/refunds", headers: staff })).json();
    const decide = () => app.inject({ method: "POST", url: `/api/staff/refunds/${refund.id}/decision`, headers: staff,
      payload: { decision: "approved", staffName: "Sam" } });
    expect((await decide()).json()).toMatchObject({ status: "approved" });
    expect((await decide()).statusCode).toBe(409);
  });

  it("explains how to switch the assistant on when no API key is set", async () => {
    const app = appWith(undefined);
    const headers = await signIn(app, jordan);
    const res = await app.inject({ method: "POST", url: `/api/conversations/${await newConversation(app, headers)}/messages`, headers,
      payload: { text: "hi" } });
    expect(res.statusCode).toBe(503);
    expect(res.json().error).toMatch(/ANTHROPIC_API_KEY/);
  });
});
