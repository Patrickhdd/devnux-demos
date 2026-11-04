# Harbor & Pine Support Agent: Constitution

## I. The model proposes, the server decides
The model chooses which tool to call. The server decides whether it is allowed. Every business rule (return window, who owns an order, what can be cancelled) lives in tool code and the database, never only in the prompt.

## II. Identity never comes from the model
The signed-in customer comes from a signed session. Tools receive it from the server, not as a model argument, so no prompt or injected text can make the agent read or change another customer's data.

## III. People approve money
The agent may submit a refund request but cannot approve one. Irreversible actions (cancelling an order) need the customer's explicit confirmation, passed as a required tool argument and stated in the tool description.

## IV. Everything the agent does is visible
Each tool call is logged with its input and a plain-language summary. Staff can read any conversation step by step. Token use is recorded per conversation.

## V. Bounded and testable
The loop has a turn limit. Tool inputs are validated before they run. The agent loop is tested against a real PostgreSQL with a scripted model, so tests are fast, free and deterministic.

## VI. Append-only history
The conversation transcript is stored exactly as the API returned it and only ever appended to. This keeps prompt caching effective and thinking blocks valid.

**Version**: 1.0.0 | **Ratified**: 2026-11-04
