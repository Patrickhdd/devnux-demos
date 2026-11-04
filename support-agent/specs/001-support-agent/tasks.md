# Tasks: AI customer support agent

## Phase 1: Setup
- [x] T001 Node.js + TypeScript API project with Fastify, pg, Zod, Anthropic SDK
- [x] T002 [P] React + Vite project
- [x] T003 [P] Docker Compose with PostgreSQL 16

## Phase 2: Foundation
- [x] T004 `schema.sql` with constraints, full-text index and one-refund-per-order index
- [x] T005 Demo data relative to today (orders inside and outside the return window)
- [x] T006 Signed demo sessions; staff key
- [x] T007 Scripted model client and Testcontainers setup for tests

## Phase 3: Agent core (US1, US2)
- [x] T008 Tests: order lookup, cross-customer lookup returns not found, refund flow, window and duplicate refusals
- [x] T009 Tools `list_orders`, `get_order`, `request_refund`, with server-side policy
- [x] T010 Agent loop: streaming, tool execution, single tool-result message per turn, turn cap, refusal and error handling
- [x] T011 Append-only transcript, token accounting, event log

## Phase 4: More tools (US3, US4)
- [x] T012 Tests: cancel needs confirmation, shipped can't cancel; help-center search; invalid input
- [x] T013 Tools `cancel_order`, `search_help_center`, `escalate_to_human`

## Phase 5: Staff (US5)
- [x] T014 Tests: staff key required, refund decided once, escalation creates ticket
- [x] T015 Staff routes and console: refunds, escalations, conversation timeline

## Phase 6: Polish
- [x] T016 Customer chat UI with streaming, tool steps, reload recovery, phone layout
- [x] T017 Dockerfiles, nginx with streaming proxy, CI
- [x] T018 README
