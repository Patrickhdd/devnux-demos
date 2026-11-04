# HTTP API

| Method | Path | Auth | Result |
|---|---|---|---|
| GET | `/api/health` | none | `{status, agentEnabled, model}` |
| GET | `/api/demo/customers` | none | demo customers to sign in as |
| POST | `/api/session` | none | `{customerId}` → `{token, customer}` (demo sign-in) |
| GET | `/api/me/orders` | customer | the customer's orders |
| POST | `/api/conversations` | customer | 201 `{id, status}` |
| GET | `/api/conversations/{id}` | customer (own) | `{id, status, events}`; 404 if not yours |
| POST | `/api/conversations/{id}/messages` | customer (own) | `{text}` → `application/x-ndjson` stream of events; 409 if a reply is in progress; 503 with no API key |
| GET | `/api/staff/overview` | staff | counts and token totals |
| GET | `/api/staff/refunds` | staff | refund requests, pending first |
| POST | `/api/staff/refunds/{id}/decision` | staff | `{decision: approved|denied, staffName}`; 409 if already decided |
| GET | `/api/staff/tickets` | staff | escalations, open and urgent first |
| POST | `/api/staff/tickets/{id}/resolve` | staff | 409 if already resolved |
| GET | `/api/staff/conversations` and `/{id}` | staff | conversations and their event timeline |

Customer auth: `Authorization: Bearer <token>`. Staff auth: `x-staff-key: <STAFF_KEY>`.

Stream events, one JSON object per line:
```
{"type":"tool_started","id":"toolu_…","name":"get_order"}
{"type":"tool_finished","id":"toolu_…","name":"get_order","summary":"Opened order HP-10482","ok":true}
{"type":"text","delta":"Your boots were delivered on "}
{"type":"done","reply":"…","conversationStatus":"open"}
```
