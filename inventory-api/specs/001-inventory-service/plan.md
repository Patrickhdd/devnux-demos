# Implementation Plan: Multi-warehouse inventory service

**Branch**: `001-inventory-service` | **Date**: 2026-11-04 | **Spec**: [spec.md](spec.md)

## Summary
A Spring Boot REST service over PostgreSQL. Stock changes are recorded in an append-only ledger and applied with conditional SQL updates, so stock can't go negative under any concurrency. Checkout holds reserve stock with an expiry. Every write is idempotent. The OpenAPI contract is validated against every response in the test suite. Terraform describes the Google Cloud deployment.

## Technical context
- **Language/version**: Java 21
- **Primary dependencies**: Spring Boot 4 (Web MVC, JDBC, Security, Validation, Actuator), Flyway
- **Storage**: PostgreSQL 16 (Cloud SQL or AlloyDB in production)
- **Testing**: JUnit 5, MockMvc, Testcontainers, `openapi-request-validator-mockmvc`
- **Target platform**: Linux container on Cloud Run
- **Performance goals**: availability reads under 50 ms p95 (SC-003)
- **Constraints**: no oversell (FR-004), idempotent writes (FR-006)
- **Scale/scope**: thousands of products, a handful of warehouses, hundreds of holds per minute at peak

## Constitution check
| Principle | How this plan complies | Status |
|---|---|---|
| I. Spec before code | `contracts/openapi.yaml` written before code; contract validated in tests | ✅ |
| II. Stock never negative | `CHECK` constraints + conditional `UPDATE` ([R1](research.md)) | ✅ |
| III. Ledger | `movement` + `movement_line` append-only; ledger test ([R3](research.md)) | ✅ |
| IV. Safe to retry | Idempotency filter on all writes ([R4](research.md)) | ✅ |
| V. Test-first, real infra | Tests per acceptance scenario on Testcontainers PostgreSQL | ✅ |
| VI. Simple | JdbcClient, no ORM; one new test dependency justified in R7 | ✅ |

## Project structure
```
specs/001-inventory-service/   spec, research, plan, data model, contract, quickstart, tasks
src/main/java/com/devnux/inventory/
  config/        security (API keys), properties, scheduling
  idempotency/   filter and store
  catalog/       products and warehouses
  stock/         stock levels, movements, low-stock alerts
  holds/         holds and expiry job
  web/           error handling
src/main/resources/db/migration/   Flyway SQL
src/test/java/...                  one test class per user story + contract validation
infra/                             Terraform: Cloud Run, Cloud SQL, Artifact Registry, Secret Manager
```

## Complexity tracking
None. No principle is violated.
