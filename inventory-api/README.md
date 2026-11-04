# Inventory service: built spec-first with Spec Kit

A multi-warehouse inventory API for an online retailer that was overselling during sales. Every system (storefront, warehouse scanners, ERP) calls one service that tracks stock per warehouse, holds stock for orders at checkout, and **never sells what isn't there**. Built by [Devnux](https://devnux.tech) as a working demo.

**Stack**: Java 21 · Spring Boot 4 · Spring Security · JDBC · Flyway · PostgreSQL 16 · OpenAPI 3 · Testcontainers · Docker · Terraform (Cloud Run, Cloud SQL) · GitHub Actions

## How it was built

This project follows the [Spec Kit](https://github.com/github/spec-kit) workflow, and the git history shows each step in order:

| Step | Spec Kit command | Output |
|---|---|---|
| 1 | `/speckit.constitution` | [Constitution](.specify/memory/constitution.md): six principles, such as "stock can never go negative" and "every response matches the contract" |
| 2 | `/speckit.specify` | [Spec](specs/001-inventory-service/spec.md): user stories, acceptance scenarios, requirements, success criteria. No technology. |
| 3 | `/speckit.clarify` | Five questions answered in the spec (hold expiry, multi-line holds, transfers, key retention, who can adjust) |
| 4 | `/speckit.plan` | [Plan](specs/001-inventory-service/plan.md) with a constitution check, [research](specs/001-inventory-service/research.md) decisions, [data model](specs/001-inventory-service/data-model.md), [OpenAPI contract](specs/001-inventory-service/contracts/openapi.yaml), [quickstart](specs/001-inventory-service/quickstart.md) |
| 5 | `/speckit.tasks` | [28 tasks](specs/001-inventory-service/tasks.md) by user story |
| 6 | `/speckit.implement` | Foundation, then **failing acceptance tests committed first**, then the code that makes them pass |

The contract isn't just documentation. Every request and response in the test suite is validated against `openapi.yaml`, and on the first run those tests caught write endpoints that didn't document a response they could return. The contract was fixed before moving on.

## What it shows

- **No oversells, proven under load.** Stock changes use conditional SQL updates (`... WHERE on_hand - reserved >= :qty`) backed by `CHECK` constraints. A test sends 10 simultaneous checkouts for 5 units: exactly 5 succeed.
- **Checkout holds.** Reserve several products in one warehouse, all or nothing. Confirm turns the hold into a shipment; release or expiry gives the stock back. The expiry job uses `FOR UPDATE SKIP LOCKED` so several instances can run it.
- **An append-only ledger.** Every change is a movement (receive, ship, adjust, transfer). Database triggers block edits and deletes, and a test checks that current stock equals the sum of the ledger.
- **Safe retries.** Any POST or PATCH with an `Idempotency-Key` runs once; a retry gets the original response with `Idempotent-Replayed: true`, and reusing a key for a different request is rejected.
- **Deadlock-free by design.** Rows are always locked in id order, for transfers and multi-line holds alike.
- **API keys with scopes** (`read`, `read+write`) and **RFC 9457 errors** with stable codes such as `insufficient-stock` and `hold-not-active`.
- **Infrastructure as code.** [Terraform](infra/) for Cloud Run with a Cloud SQL Auth Proxy sidecar, Cloud SQL PostgreSQL with backups, Artifact Registry, Secret Manager and a least-privilege service account.

## Run it

```bash
docker compose up --build
```

Then follow the [quickstart](specs/001-inventory-service/quickstart.md), or try:

```bash
KEY=dev-write-key
curl -s -X POST localhost:8080/api/v1/products -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"code":"TENT-2P","name":"Basecamp 2-person tent","reorderPoint":5}'
curl -s localhost:8080/api/v1/alerts/low-stock -H "X-API-Key: $KEY"
```

The full API is described in [`openapi.yaml`](specs/001-inventory-service/contracts/openapi.yaml), also served at http://localhost:8080/openapi.yaml.

## Test

```bash
mvn verify   # needs Docker for Testcontainers
```

24 tests, one class per user story plus the foundation, all against a real PostgreSQL.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | local `inventory` database | PostgreSQL connection (Cloud SQL and AlloyDB work unchanged) |
| `INVENTORY_API_KEYS` | two dev keys | Comma-separated `name:key:read+write` entries. **Set this in every real environment.** |
| `PORT` | `8080` | HTTP port |

## Deploy

See [infra/README.md](infra/README.md) for Google Cloud. CI checks the Terraform but does not deploy.
