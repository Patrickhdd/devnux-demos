# Quickstart: validate the feature end to end

```bash
docker compose up --build -d
KEY=dev-write-key   # demo key with read and write scopes

# Create a product and two warehouses
curl -s -X POST localhost:8080/api/v1/products -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"code":"TENT-2P","name":"Basecamp 2-person tent","reorderPoint":5}'
curl -s -X POST localhost:8080/api/v1/warehouses -H "X-API-Key: $KEY" -H 'Content-Type: application/json' -d '{"code":"PDX","name":"Portland"}'
curl -s -X POST localhost:8080/api/v1/warehouses -H "X-API-Key: $KEY" -H 'Content-Type: application/json' -d '{"code":"ATL","name":"Atlanta"}'

# Receive 10 into Portland (safe to retry with the same key)
curl -s -X POST localhost:8080/api/v1/movements -H "X-API-Key: $KEY" -H 'Idempotency-Key: po-1001' -H 'Content-Type: application/json' \
  -d '{"type":"RECEIVE","productCode":"TENT-2P","warehouseCode":"PDX","quantity":10,"reference":"PO-1001"}'

# Hold 2 for an order, then confirm it when the order ships
HOLD=$(curl -s -X POST localhost:8080/api/v1/holds -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"orderReference":"SO-5001","warehouseCode":"PDX","lines":[{"productCode":"TENT-2P","quantity":2}]}' | jq -r .id)
curl -s -X POST localhost:8080/api/v1/holds/$HOLD/confirm -H "X-API-Key: $KEY"

# On hand is now 8
curl -s localhost:8080/api/v1/products/TENT-2P/stock -H "X-API-Key: $KEY"
```

Expected: every call succeeds, the final stock shows `onHand: 8, reserved: 0, available: 8` in PDX, and the movement history has a RECEIVE of +10 and a SHIP of −2 referencing `SO-5001`.
