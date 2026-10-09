# CMPE 172 Term Project: Academic Advising Scheduling System

Spring Boot app for scheduling academic advising appointments between students and advisors.
Persistence uses Spring `JdbcTemplate` with handwritten SQL (no ORM / JPA).

- Repository: https://github.com/KananIbadzade/cmpe_172_project1
- Milestone 1 tag: `milestone-1`
- Milestone 1 walkthrough: [video](https://youtu.be/V18AfV5O15g)
- Milestone 1 report: [Doc](https://docs.google.com/document/d/1kcixdiBthgPmq50JXbeE8OWpyh0KBzus3u_8eY8tiVs/edit?tab=t.0) · [PDF](https://drive.google.com/file/d/1oJ1uNOlKSmQMU1jxG21G12HXYeieXzjw/view?usp=sharing)

- - Milestone 2 walkthrough video: [video](https://www.youtube.com/watch?v=cAGsdzeKHjU)
- - Milestone 1 report: [Doc](https://docs.google.com/document/d/1IRcGIQ7sCrrhUWwF40zel0zZk2Wm7761Cz6F6hUwZCQ/edit?tab=t.0) · [PDF](https://drive.google.com/file/d/1_2KZoIb3WMhVPkuZ7B3ufNoyJaOmGHek/view?usp=sharing)
## Milestone 2 features

- Thymeleaf + Bootstrap UI for students and advisors
- Session login with BCrypt password verification (`STUDENT` / `ADVISOR` roles)
- Browse slots with provider / service / date filters and SQL `LIMIT` / `OFFSET` pagination
- Booking with `@Transactional` + `SELECT … FOR UPDATE` and partial unique index backstop
- Owner-only appointment cancellation (HTTP 403 for non-owners)
- Advisor dashboard: view bookings, publish slots, remove unbooked slots
- Automated two-thread concurrent booking test (exactly one `201`, one `409`)

## Requirements

- Java 21
- PostgreSQL 14+ on `localhost:5432`
- Maven wrapper included (`./mvnw`)

## Database setup (one time)

```bash
psql -d postgres -c "CREATE ROLE advising LOGIN PASSWORD 'advising';"
createdb -O advising advising
```

## Configuration

| Variable      | Default                                     |
|---------------|---------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/advising` |
| `DB_USERNAME` | `advising`                                  |
| `DB_PASSWORD` | _(empty)_                                   |
| `APP_ENV`     | `local`                                     |

Set `DB_PASSWORD` if your PostgreSQL role requires a password.

## Build, test, run

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS tip
./mvnw clean test                                  # needs PostgreSQL; schema+seed reset on startup
./mvnw spring-boot:run                             # http://localhost:8080
```

`schema.sql` and `seed.sql` run on every startup (`DROP TABLE` + recreate) so demos and tests stay deterministic.

## Web UI

| Path                 | Who        | Purpose                                      |
|----------------------|------------|----------------------------------------------|
| `/`                  | Public     | Home                                         |
| `/login`             | Public     | Login form (demo quick-fill buttons)         |
| `/slots`             | Public     | Browse / filter / paginate open slots        |
| `/book/{slotId}`     | Student    | Booking form + notes                         |
| `/confirmation`      | Student    | Booking confirmation                         |
| `/my-appointments`   | Student    | History + owner-only Cancel                  |
| `/advisor`           | Advisor    | Appointments + add/remove availability slots |

JSON APIs used by tests and curl remain under `/api/...` (for example `GET /api/status`, `GET /api/slots`, `POST /api/student/appointments`).

## Seed accounts

All accounts use password `password123` (BCrypt hashes in `seed.sql`).

| Email                   | Role    |
|-------------------------|---------|
| `alex.kim@sjsu.edu`     | STUDENT |
| `jordan.lee@sjsu.edu`   | STUDENT |
| `sam.rivera@sjsu.edu`   | STUDENT |
| `maria.chen@sjsu.edu`   | ADVISOR |
| `david.nguyen@sjsu.edu` | ADVISOR |
| `priya.patel@sjsu.edu`  | ADVISOR |

## Concurrency test (double-booking proof)

`ConcurrentBookingTests` starts an embedded server, logs in two different students, and fires two HTTP `POST /api/student/appointments` calls at the **same free slot** behind a `CountDownLatch`.

Assertions:

- exactly **one** response is `201 Created`
- exactly **one** response is `409 Conflict`
- exactly one `BOOKED` row exists for that `slot_id`

That proves:

1. **Pessimistic lock** — `SELECT … FROM availability_slots WHERE id = ? FOR UPDATE` inside `@Transactional` serializes the two bookers on the slot row.
2. **Unique index backstop** — `CREATE UNIQUE INDEX uq_appointment_active_slot ON appointments(slot_id) WHERE status = 'BOOKED'` still rejects a second active booking if application checks ever race.

```bash
./mvnw -Dtest=ConcurrentBookingTests test
```

## Useful API checks

```bash
curl -i http://localhost:8080/api/status
curl -i 'http://localhost:8080/api/slots?page=0&size=5'
curl -i -H 'Content-Type: application/json' \
  -d '{"email":"alex.kim@sjsu.edu","password":"password123"}' \
  -c cookies.txt http://localhost:8080/login
```
