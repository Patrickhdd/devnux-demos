-- Clinic booking schema.
-- Double booking is prevented by the database itself with an exclusion
-- constraint, so two requests racing for the same slot can never both win.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(200) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('PATIENT', 'DOCTOR', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE doctor (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL UNIQUE REFERENCES app_user (id),
    specialty    VARCHAR(80)  NOT NULL,
    bio          VARCHAR(500),
    slot_minutes INT          NOT NULL DEFAULT 30 CHECK (slot_minutes IN (15, 20, 30, 45, 60))
);

CREATE TABLE working_hours (
    id          BIGSERIAL PRIMARY KEY,
    doctor_id   BIGINT   NOT NULL REFERENCES doctor (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO: 1 = Monday
    start_time  TIME     NOT NULL,
    end_time    TIME     NOT NULL,
    CHECK (end_time > start_time)
);
CREATE INDEX working_hours_doctor_idx ON working_hours (doctor_id, day_of_week);

CREATE TABLE appointment (
    id          BIGSERIAL PRIMARY KEY,
    doctor_id   BIGINT       NOT NULL REFERENCES doctor (id),
    patient_id  BIGINT       NOT NULL REFERENCES app_user (id),
    starts_at   TIMESTAMPTZ  NOT NULL,
    ends_at     TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED')),
    reason      VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0,
    CHECK (ends_at > starts_at),
    CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
        doctor_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'BOOKED')
);
CREATE INDEX appointment_patient_idx ON appointment (patient_id, starts_at);
CREATE INDEX appointment_doctor_idx ON appointment (doctor_id, starts_at);
