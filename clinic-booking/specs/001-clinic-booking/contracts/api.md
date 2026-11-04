# REST API contract

All bodies are JSON. Times are ISO-8601 instants. Errors are `application/problem+json` with `status`, `title` and `detail`.

| Method | Path | Who | Result |
|---|---|---|---|
| POST | `/api/auth/register` | anyone | 201 `{token, expiresAt, user}`; 409 if the email exists |
| POST | `/api/auth/login` | anyone | 200 `{token, expiresAt, user}`; 401 on a wrong password |
| GET | `/api/doctors` | anyone | list of doctors |
| GET | `/api/doctors/{id}` | anyone | doctor with working hours |
| GET | `/api/doctors/{id}/slots?date=YYYY-MM-DD` | anyone | `[{startsAt, endsAt, available}]` in the clinic's day |
| POST | `/api/appointments` | patient | body `{doctorId, startsAt, reason?}`; 201 appointment; 400 not a slot; 409 taken |
| GET | `/api/appointments/mine` | patient | the caller's appointments |
| POST | `/api/appointments/{id}/cancel` | patient (own) or doctor (own) | 200 appointment; 404 not yours; 409 past or inactive |
| GET | `/api/doctor/schedule?from=YYYY-MM-DD&days=7` | doctor | booked and completed appointments in the range |
| POST | `/api/doctor/appointments/{id}/complete` | doctor (own) | 200 appointment; 409 not started yet |
| GET | `/actuator/health` | anyone | `{status: "UP"}` |

Send the token as `Authorization: Bearer <token>`.
