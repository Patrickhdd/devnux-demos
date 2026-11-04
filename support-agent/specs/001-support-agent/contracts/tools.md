# Agent tools

All tools run as the signed-in customer. Inputs are validated with Zod before running; invalid input returns `{ "error": "invalid_input" }` to the model with `is_error: true`.

| Tool | Input | What it does | Refuses with |
|---|---|---|---|
| `search_help_center` | `query` | Top 3 help articles by PostgreSQL full-text rank | — |
| `list_orders` | none | The customer's 10 latest orders with totals | — |
| `get_order` | `order_number` (`HP-12345`) | Items, status, tracking, address, `can_cancel`, `can_request_refund` | `not_found` |
| `request_refund` | `order_number`, `reason` | Creates a `pending_review` refund for the order total | `not_found`, `not_delivered`, `outside_return_window`, `already_requested` |
| `cancel_order` | `order_number`, `customer_confirmed` | Cancels a processing order | `confirmation_required`, `not_found`, `cannot_cancel` |
| `escalate_to_human` | `summary`, `priority` | Opens a ticket and marks the conversation escalated | — |
