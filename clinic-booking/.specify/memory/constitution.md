# Northside Clinic Booking: Constitution

These principles guide every spec, plan and task in this project. When a plan conflicts with them, the plan changes.

## I. The database guards the invariants
Rules that must never break, such as "one doctor cannot have two booked appointments at the same time", are enforced by PostgreSQL constraints, not only by application code. Application checks exist to give friendly errors; the database is the final word.

## II. Time is always explicit
All instants are stored as `timestamptz` (UTC). Working hours are local times in the clinic's time zone, which is configuration (`clinic.zone`). The API speaks ISO-8601 with offsets. The UI shows clinic time and says so.

## III. Least privilege by default
Every endpoint is denied unless a rule allows it. Patients only see their own appointments. Doctors only see their own schedule. Resources a caller may not see return 404, not 403, so ids cannot be probed.

## IV. Tests prove the risky paths
Each user story has an integration test against a real PostgreSQL (Testcontainers), including the concurrent double-booking case. No mocks for the database.

## V. Small and boring
Spring Boot defaults, plain JPA, Flyway migrations, one React app. No new dependency without a reason written in the plan.

## Governance
Changes to this constitution are made by pull request and noted in the plan of the feature that needed them.

**Version**: 1.0.0 | **Ratified**: 2026-11-04
