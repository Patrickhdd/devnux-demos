# Implementation Plan: Clinic appointment booking

**Branch**: `001-clinic-booking` | **Spec**: [spec.md](spec.md)

## Summary
A Spring Boot REST API backed by PostgreSQL, and a React single-page app. A PostgreSQL exclusion constraint makes double booking impossible; the API turns the constraint violation into a friendly 409.

## Technical context
- **Language**: Java 21, TypeScript
- **Backend**: Spring Boot 4 (Web MVC, Security with OAuth2 resource server, Data JPA, Validation, Actuator), Flyway
- **Database**: PostgreSQL 16 with the `btree_gist` extension. Works on Cloud SQL or AlloyDB unchanged.
- **Frontend**: React 19, Vite, no UI library
- **Auth**: stateless HS256 JWT issued by the API; role in a `role` claim
- **Testing**: JUnit 5, MockMvc, Testcontainers PostgreSQL
- **Deploy target**: containers (Docker Compose locally, Cloud Run or any container host)

## Constitution check
- I. Overlap rule is an `EXCLUDE USING gist` constraint. ✅
- II. `timestamptz` everywhere; `clinic.zone` config. ✅
- III. Deny-by-default security config; ownership checks return 404. ✅
- IV. `BookingFlowTest` covers every story and the concurrency case. ✅
- V. No dependencies beyond Spring Boot starters, Flyway and Testcontainers. ✅

## Key decisions
| Decision | Why | Rejected alternative |
|---|---|---|
| Exclusion constraint on `tstzrange(starts_at, ends_at)` where status is BOOKED | Correct under any concurrency, any number of API instances | `SELECT ... FOR UPDATE` (only safe if every writer remembers it) |
| Slots computed on read from working hours | No slot table to keep in sync | Pre-generated slot rows |
| Partial constraint (`WHERE status = 'BOOKED'`) | Cancelled visits free the slot without deleting history | Deleting cancelled rows |
| JWT signed by the API | No external identity provider needed for a demo | Keycloak / Auth0 (better for production) |

## Project structure
```
api/   Spring Boot service (controllers, services, JPA entities, Flyway migrations, tests)
web/   React app (patient booking, my appointments, doctor schedule)
docker-compose.yml   db + api + web on http://localhost:3000
```
