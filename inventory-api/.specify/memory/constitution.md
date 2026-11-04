<!--
Sync impact report
Version: 1.0.0 (initial ratification)
Templates checked: plan-template.md ✅  spec-template.md ✅  tasks-template.md ✅
-->
# Inventory Service: Constitution

## Core principles

### I. Spec before code
Every change starts as a spec in `specs/NNN-name/`. The OpenAPI document in `contracts/` is the source of truth for the HTTP API; code that disagrees with it is a bug. Implementation begins only after `tasks.md` exists.

### II. Stock can never go negative
On-hand and reserved quantities are protected by database constraints and by conditional updates, not by read-then-write checks in Java. Correctness must hold with many API instances running at once.

### III. Every change to stock is a ledger entry
Stock levels change only by recording a movement (receive, ship, adjust, transfer) in an append-only ledger. Current levels are a projection that can be rebuilt from the ledger.

### IV. Safe to retry
Every write endpoint accepts an `Idempotency-Key`. Retrying the same request returns the original result; reusing a key for a different request is rejected.

### V. Test-first, against real infrastructure
Acceptance scenarios become tests before the code that satisfies them. Tests run against PostgreSQL in Testcontainers. Every API response in the test suite is validated against the OpenAPI contract.

### VI. Simple, observable, boring
Spring Boot defaults, plain SQL where it is clearer than JPA, structured errors (RFC 9457), health and metrics endpoints. No new dependency without a line in `research.md` saying why.

## Constraints
- Java 21, Spring Boot 4, PostgreSQL 16. Runs unchanged on Cloud SQL or AlloyDB.
- Deployable as one container; infrastructure is code (`infra/`).

## Governance
This constitution overrides other practices. Amendments are made by pull request, bump the version, and list affected templates in the sync impact report above. Each `plan.md` includes a Constitution Check that must pass before tasks are generated.

**Version**: 1.0.0 | **Ratified**: 2026-11-04 | **Last amended**: 2026-11-04
