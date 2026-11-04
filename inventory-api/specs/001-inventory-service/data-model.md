# Data model

```
product        (id, code UNIQUE upper-case, name, reorder_point >= 0, created_at)
warehouse      (id, code UNIQUE upper-case, name, created_at)
stock_level    (product_id, warehouse_id, on_hand >= 0, reserved >= 0, reserved <= on_hand, updated_at)  PK (product_id, warehouse_id)
movement       (id, type RECEIVE|SHIP|ADJUST|TRANSFER, product_id, reason, reference, hold_id, created_by, created_at)   append-only
movement_line  (id, movement_id, warehouse_id, delta <> 0)                                                    append-only
hold           (id uuid, order_reference, warehouse_id, status ACTIVE|CONFIRMED|RELEASED|EXPIRED, expires_at, created_by, created_at, closed_at)
hold_line      (hold_id, product_id, quantity > 0)  PK (hold_id, product_id)
idempotency_record (client, key, request_hash, state IN_PROGRESS|COMPLETED, response_status, response_body, created_at)  PK (client, key)
```

## Derived values
- `available = on_hand - reserved`
- Low stock: `sum(available) over all warehouses <= reorder_point`

## State transitions (hold)
```
ACTIVE → CONFIRMED   confirm before expires_at: reserved −q, on_hand −q, SHIP movement per line
ACTIVE → RELEASED    release: reserved −q
ACTIVE → EXPIRED     expiry job after expires_at: reserved −q
```
Any other transition returns 409 `hold-not-active`.

## Movement rules
| Type | Lines | Condition |
|---|---|---|
| RECEIVE | +q in warehouse | q > 0 |
| SHIP | −q in warehouse | q > 0 and q <= available |
| ADJUST | ±q in warehouse | q ≠ 0, reason required, on_hand + q >= reserved |
| TRANSFER | −q in source, +q in destination | q > 0, q <= available in source, warehouses differ |
