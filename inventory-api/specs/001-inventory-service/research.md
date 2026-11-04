# Research: Multi-warehouse inventory service

## R1. Preventing oversells under concurrency
- **Decision**: Conditional updates (`UPDATE stock_level SET reserved = reserved + :q WHERE ... AND on_hand - reserved >= :q`) inside one transaction, plus `CHECK (reserved <= on_hand)` and `CHECK (on_hand >= 0)` constraints.
- **Rationale**: The row lock taken by the UPDATE serialises competing writers on the same stock row; the WHERE clause re-evaluates after the lock, so no request can see stale availability. Works with any number of API instances.
- **Alternatives**: `SELECT ... FOR UPDATE` then write (correct but two round trips and easy to forget); optimistic version column with retries (more code and retries under contention); `SERIALIZABLE` isolation (retries on every conflict).

## R2. Multi-line holds without deadlocks
- **Decision**: Update stock rows in ascending product id order within the transaction.
- **Rationale**: Two holds that touch the same products always lock them in the same order, so they wait instead of deadlocking.

## R3. Ledger and projection
- **Decision**: `movement` (header) + `movement_line` (warehouse, delta) is append-only; `stock_level` is updated in the same transaction. A test checks that `stock_level.on_hand` equals the sum of lines for every product and warehouse.
- **Alternatives**: Computing stock from the ledger on every read (slow as history grows); event sourcing framework (heavy for one service).

## R4. Idempotency
- **Decision**: A servlet filter for POST/PATCH under `/api/v1` with an `Idempotency-Key` header. It stores `(client, key, request hash)` before the request runs and the response after. Same key and body replays the stored response with `Idempotent-Replayed: true`; same key with a different body returns 422; a request still in progress returns 409. 5xx responses are not stored, so they can be retried. Records older than 24 hours are deleted hourly.
- **Rationale**: One implementation covers every write endpoint, and the unique primary key makes concurrent duplicates safe.

## R5. Data access
- **Decision**: Spring `JdbcClient` with plain SQL. No JPA.
- **Rationale**: The important logic is conditional SQL updates and upserts; an ORM would hide it. Fewer moving parts (constitution VI).

## R6. Authentication
- **Decision**: API keys in the `X-API-Key` header, each with a name and scopes (`inventory:read`, `inventory:write`), configured by environment. Implemented as a Spring Security filter.
- **Rationale**: Callers are other systems (storefront, warehouse scanners, ERP), not people. Keys are simple to rotate per system. OAuth client credentials is the upgrade path.

## R7. Contract testing
- **Decision**: `com.atlassian.oai:openapi-request-validator-mockmvc` validates every MockMvc request and response in the test suite against `contracts/openapi.yaml`.
- **Rationale**: Enforces "the contract is the source of truth" (constitution I) on every build with no hand-written schema assertions.

## R8. Hold expiry
- **Decision**: A scheduled job every 30 seconds releases `ACTIVE` holds whose `expires_at` has passed, using `FOR UPDATE SKIP LOCKED` in batches so several instances can run it safely. Confirm also checks `expires_at > now()`, so an expired hold can never be confirmed even before the job runs.

## R9. Hosting
- **Decision**: Container on Google Cloud Run, PostgreSQL on Cloud SQL, image in Artifact Registry, secrets in Secret Manager, described in Terraform under `infra/`.
- **Rationale**: Scales to zero, managed database, no servers to patch. The service has no GCP-specific code, so AlloyDB or another cloud works too.
