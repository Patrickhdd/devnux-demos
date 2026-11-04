# Data model

```
customer (id, email UNIQUE, full_name)
product (id, sku UNIQUE, name, price_cents > 0)
shop_order (id, order_number UNIQUE, customer_id, status processing|shipped|delivered|cancelled,
            placed_at, shipped_at, delivered_at, carrier, tracking_code, ship_to)
order_item (id, order_id, product_id, quantity > 0, price_cents)
help_article (id, slug UNIQUE, title, body, search tsvector GENERATED, GIN index)
conversation (id uuid, customer_id, status open|escalated|closed, transcript jsonb, input_tokens, output_tokens)
conversation_event (id, conversation_id, kind customer|agent|tool|error, payload jsonb)
refund_request (id, reference UNIQUE, order_id, conversation_id, amount_cents, reason,
                status pending_review|approved|denied, decided_by, decided_at)
support_ticket (id, reference UNIQUE, conversation_id, customer_id, priority low|normal|urgent, summary, status open|resolved)
```

## Rules in the database
- `refund_one_per_order`: a partial unique index allows one non-denied refund request per order, even under concurrent requests.
- Status columns have CHECK constraints, so a bug can't write an unknown status.
