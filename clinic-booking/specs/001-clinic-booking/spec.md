# Feature Specification: Clinic appointment booking

**Feature branch**: `001-clinic-booking`
**Status**: Implemented
**Input**: "A small clinic wants patients to book visits online and doctors to see their day, without ever double-booking a doctor."

## User scenarios and testing

### User story 1: A patient books a visit (Priority: P1)
A patient picks a doctor, a day and a free time, adds an optional reason, and confirms.

**Why this priority**: It is the reason the product exists.

**Independent test**: Sign in as a patient, book the first free slot, and see it under "My appointments".

**Acceptance scenarios**
1. **Given** a free slot, **when** the patient confirms, **then** the appointment is saved as BOOKED and the slot is no longer offered.
2. **Given** a slot another patient just took, **when** the patient confirms, **then** they see "Sorry, that slot was just booked by someone else" (HTTP 409) and nothing is saved.
3. **Given** a time that is not one of the doctor's slots (wrong minute, outside hours, in the past), **when** it is submitted, **then** the API returns 400.

### User story 2: A patient manages their visits (Priority: P2)
A patient sees upcoming and past appointments and can cancel an upcoming one.

**Acceptance scenarios**
1. **Given** an upcoming appointment, **when** the patient cancels it, **then** it becomes CANCELLED and the slot is offered again.
2. **Given** another patient's appointment id, **when** a patient tries to cancel it, **then** the API returns 404.

### User story 3: A doctor runs their day (Priority: P2)
A doctor sees their booked appointments for a week, can cancel a future one and can mark a started one as seen.

**Acceptance scenarios**
1. **Given** booked appointments, **when** the doctor opens "My schedule", **then** they see them grouped by day in clinic time.
2. **Given** an appointment that has not started, **when** the doctor tries to mark it as seen, **then** the API returns 409.
3. **Given** a patient token, **when** it calls a doctor endpoint, **then** the API returns 403.

### Edge cases
- Four patients press "Confirm" on the same slot at the same moment: exactly one succeeds, three get 409.
- Booking more than 30 days ahead is rejected.
- Daylight-saving changes: slots are generated in clinic local time, so a 9:00 AM slot stays 9:00 AM.

## Requirements

### Functional requirements
- **FR-001**: Anyone can list doctors and the free slots for a doctor on a given date.
- **FR-002**: Patients can register with email, password (8+ characters) and full name, and sign in.
- **FR-003**: Signed-in patients can book a free slot within the booking window (default 30 days).
- **FR-004**: The system MUST NOT allow two BOOKED appointments for the same doctor to overlap, even under concurrent requests.
- **FR-005**: Patients can list and cancel their own future appointments.
- **FR-006**: Doctors can list their schedule for a date range, cancel future appointments and mark started ones as COMPLETED.
- **FR-007**: Errors use RFC 9457 problem details with a human-readable `detail`.

### Key entities
- **User**: email, password hash, full name, role (PATIENT or DOCTOR).
- **Doctor**: a user with a specialty, bio and visit length in minutes.
- **Working hours**: a doctor's start and end time for a day of the week.
- **Appointment**: doctor, patient, start, end, status (BOOKED, CANCELLED, COMPLETED), reason.

## Success criteria
- **SC-001**: A patient can go from sign-in to a confirmed booking in under 30 seconds.
- **SC-002**: Zero double bookings in the concurrent booking test, run on every build.
- **SC-003**: The whole system starts with one command (`docker compose up --build`).
