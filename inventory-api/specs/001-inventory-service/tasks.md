# Tasks: Multi-warehouse inventory service

**Input**: [plan.md](plan.md), [spec.md](spec.md), [data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml)
**Tests**: required by the constitution (V). Write each story's tests first and see them fail.
Legend: `[P]` can run in parallel (different files). `[USn]` maps to a user story.

## Phase 1: Setup
- [x] T001 Create Maven project `pom.xml` (Spring Boot 4, Java 21) with Web MVC, JDBC, Security, Validation, Actuator, Flyway, PostgreSQL
- [x] T002 [P] Add test dependencies: Testcontainers PostgreSQL, openapi-request-validator-mockmvc
- [x] T003 [P] `application.yml` with database, API keys and hold settings from environment

## Phase 2: Foundation (blocks all stories)
- [x] T004 Flyway `V1__schema.sql`: all tables and constraints from data-model.md
- [x] T005 [P] API key authentication with read/write scopes in `config/SecurityConfig.java` and `config/ApiKeyFilter.java`
- [x] T006 [P] Problem-details error handling with stable `code` values in `web/ErrorHandling.java`
- [x] T007 Idempotency filter and store in `idempotency/`
- [x] T008 Test base class that validates every request and response against `contracts/openapi.yaml`
- [x] T009 [P] Products and warehouses (create, list, get, update) in `catalog/`

## Phase 3: US1 + US2, know what can be sold and record changes (P1) 🎯 MVP
- [x] T010 [US1] Test: availability per warehouse and total; unknown product 404
- [x] T011 [US2] Test: receive, ship, adjust, transfer; overship rejected with nothing changed; transfer is all-or-nothing
- [x] T012 [US2] Test: retry with the same Idempotency-Key applies once and replays; different body returns 422
- [x] T013 [US2] Test: stock levels equal the sum of the ledger after a mixed workload
- [x] T014 [US1] Stock query in `stock/StockService.java` and `GET /products/{code}/stock`
- [x] T015 [US2] Movements with conditional updates in `stock/MovementService.java`, `POST /movements`, `GET /products/{code}/movements`

## Phase 4: US3, hold stock at checkout (P1)
- [x] T016 [US3] Test: hold reduces available; confirm ships; release restores; second confirm 409
- [x] T017 [US3] Test: 10 concurrent holds of 1 against 5 available, exactly 5 succeed
- [x] T018 [US3] Test: multi-line hold with one short line holds nothing
- [x] T019 [US3] Test: expired hold can't be confirmed and the job releases it
- [x] T020 [US3] `holds/HoldService.java` with ordered locking, `holds/HoldController.java`
- [x] T021 [US3] Expiry job with `FOR UPDATE SKIP LOCKED` in `holds/HoldExpiryJob.java`

## Phase 5: US4, restock alerts (P2)
- [x] T022 [US4] Test: product at or below reorder point appears with shortfall
- [x] T023 [US4] `GET /alerts/low-stock`

## Phase 6: Polish
- [x] T024 [P] Serve the contract at `/openapi.yaml`
- [ ] T025 [P] Dockerfile and Docker Compose
- [ ] T026 [P] Terraform for Cloud Run, Cloud SQL, Artifact Registry and Secret Manager in `infra/`
- [ ] T027 CI: `mvn verify`, image build, `terraform validate`
- [ ] T028 README and run quickstart.md end to end

## Dependencies
Setup → Foundation → US1+US2 → US3 → US4 → Polish. US3 needs movements (confirm records SHIP). US4 only needs stock levels.
