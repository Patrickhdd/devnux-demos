# Implementation Plan: AI customer support agent

**Branch**: `001-support-agent` | **Spec**: [spec.md](spec.md)

## Summary
A Node.js API runs a tool-using Claude agent over the store's PostgreSQL data. A React app gives customers a streaming chat and staff a review console.

## Technical context
- **Language**: TypeScript (Node.js 22)
- **API**: Fastify 5, `pg`, Zod 4 for tool input validation
- **AI**: Anthropic TypeScript SDK, model `claude-opus-5-5`, adaptive thinking, effort `medium` (configurable)
- **Database**: PostgreSQL 16 (full-text search with a generated `tsvector` column and GIN index)
- **Frontend**: React 19, Vite
- **Testing**: Vitest, Testcontainers PostgreSQL, scripted model client
- **Deploy**: containers (Docker Compose locally; Cloud Run or any container host)

## Constitution check
- I. Return window, ownership and cancel rules are in `src/tools.ts` and SQL. ✅
- II. `ToolContext.customerId` comes from the HMAC-signed session. ✅
- III. `request_refund` only creates `pending_review`; `cancel_order` requires `customer_confirmed`. ✅
- IV. `conversation_event` rows for every tool call; staff transcript view. ✅
- V. `AGENT_MAX_TURNS`; Zod validation; tests use a scripted model. ✅
- VI. Transcript appended as returned; test checks the prefix is unchanged. ✅

## Key decisions
| Decision | Why | Rejected alternative |
|---|---|---|
| Own agent loop over the streaming Messages API | Lets us stream text, show each tool step, log it, cap turns, and store an exact transcript | SDK tool runner (fine, but less visible for a teaching demo) |
| Customer id injected by the server | Prompt injection can't widen access | Model passes customer email (spoofable) |
| Refunds go to a review queue | Money needs a person | Agent issues refunds directly |
| Prompt caching on the whole request | Long system prompt + tools + history are re-sent each turn; caching makes repeats cheap | No caching |
| `fallbacks: "default"` (server-side fallback beta) | A policy decline is retried on a fallback model inside the same call | Client-side retry logic |
| NDJSON over a `fetch` stream | Works through nginx with buffering off; simple to parse | WebSockets |

## Project structure
```
server/src/agent.ts    the agent loop and system prompt
server/src/tools.ts    the six tools: schemas, policy checks, SQL
server/src/app.ts      HTTP routes (customer chat, staff console)
server/src/schema.sql  tables, constraints, full-text index
server/test/           Testcontainers + scripted-model tests
web/src/               React chat and staff console
```
