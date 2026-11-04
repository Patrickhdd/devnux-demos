# Data model

```
app_user (id, email UNIQUE (stored lower-case), password_hash, full_name, role PATIENT|DOCTOR|ADMIN, created_at)
doctor (id, user_id → app_user UNIQUE, specialty, bio, slot_minutes 15|20|30|45|60)
working_hours (id, doctor_id → doctor, day_of_week 1..7 ISO, start_time, end_time, CHECK start < end)
appointment (id, doctor_id → doctor, patient_id → app_user, starts_at timestamptz, ends_at timestamptz,
             status BOOKED|CANCELLED|COMPLETED, reason, created_at, version)
```

## The rule that matters

```sql
CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
  doctor_id WITH =,
  tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status = 'BOOKED')
```

Two BOOKED rows for the same doctor whose time ranges overlap cannot exist. The range is half-open, so a 9:00–9:30 visit and a 9:30–10:00 visit are both allowed.

## State changes

```
BOOKED → CANCELLED   patient or doctor, only before the start time
BOOKED → COMPLETED   doctor, only after the start time
```
