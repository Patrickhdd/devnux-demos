# Tasks: Clinic appointment booking

Legend: [P] can run in parallel. [US1] etc. map to the user stories in spec.md.

## Phase 1: Setup
- [x] T001 Create the Spring Boot project (`api/pom.xml`) with Web MVC, Security, JPA, Flyway, Validation, Actuator, Testcontainers
- [x] T002 [P] Create the React + Vite project in `web/`
- [x] T003 [P] Add Docker Compose with PostgreSQL 16

## Phase 2: Foundation
- [x] T004 Flyway `V1__schema.sql` with tables, checks and the `appointment_no_overlap` exclusion constraint
- [x] T005 JPA entities and repositories
- [x] T006 Security config: JWT issue and verify, role mapping, deny by default, CORS
- [x] T007 Problem-details error handler
- [x] T008 Demo data seeder (3 doctors, 2 patients, sample visits)

## Phase 3: User story 1, patient books a visit (P1)
- [x] T009 [US1] Test: book, slot disappears, double booking gets 409, non-slot gets 400
- [x] T010 [US1] Test: four concurrent bookings, exactly one wins
- [x] T011 [US1] `SlotService` generates slots from working hours in clinic time
- [x] T012 [US1] `AppointmentService.book` maps the constraint violation to 409
- [x] T013 [US1] Endpoints: doctors, slots, book; register and login
- [x] T014 [US1] UI: sign in, choose doctor, day and time, confirm

## Phase 4: User story 2, patient manages visits (P2)
- [x] T015 [US2] Test: cancel frees the slot; other patients get 404
- [x] T016 [US2] Endpoints: mine, cancel
- [x] T017 [US2] UI: "My appointments" with upcoming and past

## Phase 5: User story 3, doctor runs their day (P2)
- [x] T018 [US3] Test: schedule, role checks (401/403)
- [x] T019 [US3] Endpoints: schedule, complete (409 if not started)
- [x] T020 [US3] UI: week view, cancel future, mark started as seen

## Phase 6: Polish
- [x] T021 Dockerfiles for api and web; nginx proxies `/api`
- [x] T022 CI: `mvn verify` and `npm run build` on every push
- [x] T023 README with screenshots
