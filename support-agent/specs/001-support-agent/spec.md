# Feature Specification: AI customer support agent

**Feature branch**: `001-support-agent`
**Status**: Implemented
**Input**: "An online store wants an assistant that answers customers right away, looks up their real orders, starts refunds and cancellations within policy, and hands anything else to a person, without ever giving away money on its own."

## User scenarios and testing

### User story 1: Customer gets an answer about an order (Priority: P1)
A signed-in customer asks where an order is, or what's in it. The assistant looks it up and answers with the real status, carrier and tracking code.

**Independent test**: Sign in as Jordan, ask "Where is my backpack order?", and see the shipped status and USPS tracking code.

**Acceptance scenarios**
1. **Given** a customer with several orders, **when** they ask without an order number, **then** the agent lists their orders and picks the right one.
2. **Given** an order number from another customer, **when** the agent looks it up, **then** the tool returns "not found" and no data from the other account.

### User story 2: Customer asks for a refund (Priority: P1)
**Acceptance scenarios**
1. **Given** a delivered order within 30 days, **when** the customer asks for a refund, **then** a refund request is created as `pending_review` with the server-computed amount and the customer gets its reference.
2. **Given** an order delivered more than 30 days ago, **when** a refund is requested, **then** the tool refuses with `outside_return_window` and the agent offers to escalate.
3. **Given** an order that already has a refund request, **when** another is requested, **then** the tool refuses with `already_requested`.

### User story 3: Customer cancels an order (Priority: P2)
**Acceptance scenarios**
1. **Given** a processing order, **when** the agent calls `cancel_order` without `customer_confirmed: true`, **then** nothing changes.
2. **Given** confirmation, **then** the order becomes `cancelled`. A shipped order cannot be cancelled.

### User story 4: Policy questions (Priority: P2)
The agent searches the help center (PostgreSQL full-text search) and answers from the articles.

### User story 5: Staff stay in control (Priority: P1)
Staff open a console with a staff key, approve or deny refund requests, resolve escalations, and read each conversation with every tool call and its input.

**Acceptance scenarios**
1. **Given** a pending refund, **when** staff approve it, **then** it becomes `approved`. Deciding it again returns 409.
2. **Given** the customer asks for a person, **when** the agent escalates, **then** a ticket is created and the conversation is marked `escalated`.

### Edge cases
- The model sends malformed tool input (for example an order number with SQL in it): the tool is not run and the model gets `invalid_input`.
- The model keeps calling tools: the loop stops after `AGENT_MAX_TURNS` (default 8) with a polite message.
- The model declines for policy reasons: the API's server-side fallback is tried first; if the final answer is still a refusal the customer is offered a person.
- No API key configured: everything except the chat works, and the chat says how to switch it on.

## Requirements
- **FR-001**: Tools are scoped to the signed-in customer by the server.
- **FR-002**: Refund amounts and eligibility are computed by the server, never taken from the model.
- **FR-003**: Refund requests always need a staff decision.
- **FR-004**: Replies stream to the browser as they are written, with each tool step shown in plain language.
- **FR-005**: Conversations survive a page reload.
- **FR-006**: Every tool call is logged with input, summary and success.

## Success criteria
- **SC-001**: Zero cross-customer data in tests that try it.
- **SC-002**: Zero refunds approved without a staff action.
- **SC-003**: First words of a reply appear while the agent is still working (streaming).
