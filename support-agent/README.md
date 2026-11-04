# Harbor & Pine: AI customer support agent

A support assistant for a (fictional) online outdoor store. It chats with signed-in customers, looks up their real orders in PostgreSQL, answers policy questions from the help center, submits refunds and cancellations within policy, and hands anything else to a person. Staff review every refund in a console and can read exactly what the assistant did. Built by [Devnux](https://devnux.tech) as a working demo.

**Stack**: TypeScript · Node.js 22 · Fastify · Claude (Anthropic SDK, tool use, streaming) · PostgreSQL 16 · React 19 · Vite · Docker · Vitest · Testcontainers

![Customer chat](docs/chat.png)

## What it shows

- **A real agent loop.** Claude decides which of six tools to call (search help center, list orders, get order, request refund, cancel order, escalate to a person), the server runs them, and the loop continues until Claude answers. Replies stream to the browser word by word, with each step shown in plain language ("Opened order HP-10482").
- **Guardrails in code, not just in the prompt.**
  - The customer's identity comes from a signed session and is injected into every tool by the server. Whatever the model or a crafted message asks for, it can only see that customer's orders. A test tries.
  - The 30-day return window, refund amounts and "only processing orders can be cancelled" are enforced in tool code and SQL.
  - Refunds are only ever *requested*. A person approves them in the staff console.
  - Cancelling needs the customer's explicit confirmation as a required tool argument.
  - Tool inputs are validated with Zod before anything runs. The loop stops after 8 model turns.
- **Full audit trail.** Every tool call is logged with its input and result summary, and the staff console shows each conversation step by step with token use.
- **Production-minded API use.** Prompt caching on the whole request, an append-only transcript stored exactly as returned (so thinking blocks and the cache stay valid), adaptive thinking with a configurable effort level, server-side fallback if the model declines, and typed SDK error handling.
- **Tested without spending tokens.** The agent loop runs against a real PostgreSQL (Testcontainers) with a scripted model that plays back tool calls and checks what the agent sends back. 12 tests, a few seconds, no API key.
- **Spec-driven.** Planned with [Spec Kit](https://github.com/github/spec-kit): [constitution](.specify/memory/constitution.md), [spec](specs/001-support-agent/spec.md), [plan](specs/001-support-agent/plan.md), [data model](specs/001-support-agent/data-model.md), [tool contract](specs/001-support-agent/contracts/tools.md), [API contract](specs/001-support-agent/contracts/api.md) and [tasks](specs/001-support-agent/tasks.md).

## Run it

You need Docker and an [Anthropic API key](https://console.anthropic.com/).

```bash
echo "ANTHROPIC_API_KEY=sk-ant-..." > .env
docker compose up --build
```

- Customer chat: http://localhost:3001 (pick Jordan or Taylor)
- Staff console: http://localhost:3001/staff (key `demo-staff`)

Things to try as Jordan:
- "Where is my backpack order?"
- "My boots from HP-10482 are too small. Can I return them?" Then approve the refund in the staff console.
- "Can I return the rain jacket I bought last month?" It's outside the window, so the assistant offers a person.
- "Please cancel my tent order." It asks you to confirm first.
- "What's in order HP-10455?" That's Taylor's order, so it isn't found.

Without a key everything else still runs, and the chat says how to switch the assistant on.

## Develop

```bash
docker run -d --name support-db -e POSTGRES_USER=support -e POSTGRES_PASSWORD=support -e POSTGRES_DB=support -p 5432:5432 postgres:16-alpine

cd server && npm install
ANTHROPIC_API_KEY=sk-ant-... npm run dev     # API on :8080
npm test                                    # needs Docker for Testcontainers

cd ../web && npm install && npm run dev      # web on :5173, proxies /api to :8080
```

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `ANTHROPIC_API_KEY` | none | Turns the assistant on |
| `AGENT_MODEL` | `claude-opus-5-5` | Claude model |
| `AGENT_EFFORT` | `medium` | `low` / `medium` / `high` / `xhigh` / `max`. Lower is faster and cheaper |
| `AGENT_MAX_TURNS` | `8` | Model turns allowed per customer message |
| `DATABASE_URL` | `postgres://support:support@localhost:5432/support` | PostgreSQL connection |
| `SESSION_SECRET` | dev value | Signs customer sessions. **Set this in production.** |
| `STAFF_KEY` | `demo-staff` | Staff console key. **Set this in production.** |
| `SEED_DEMO_DATA` | `true` | Create the demo store on first start |

## How the agent works

```
customer message
  → append to transcript
  → stream request (system prompt + 6 tools + full history, cached)
      ├─ text  → streamed to the browser
      └─ tool calls → validate input → run as the signed-in customer → log → all results in one message
  → repeat until Claude answers without a tool (max 8 turns)
  → save transcript, token use and events
```

The loop is in [`server/src/agent.ts`](server/src/agent.ts) and the tools, with their policy checks, are in [`server/src/tools.ts`](server/src/tools.ts).

## In a real store

The demo sign-in and shared staff key would be replaced by the store's own login and staff SSO, refunds would call the payment provider after approval, and the help center would sync from the store's CMS.
