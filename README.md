# CMPE 172 Term Project: Academic Advising Scheduling System

[![build](https://github.com/KananIbadzade/cmpe_172_project1/actions/workflows/build.yml/badge.svg)](https://github.com/KananIbadzade/cmpe_172_project1/actions/workflows/build.yml)

Spring Boot app for scheduling academic advising appointments between students and advisors.
Persistence uses Spring `JdbcTemplate` with handwritten SQL (no ORM / JPA).

- Repository: https://github.com/KananIbadzade/cmpe_172_project1
- Milestone tags: `milestone-1`, `milestone-2`, `milestone-3`
- Milestone 1 walkthrough: [video](https://youtu.be/V18AfV5O15g)
- Milestone 1 report: [Doc](https://docs.google.com/document/d/1kcixdiBthgPmq50JXbeE8OWpyh0KBzus3u_8eY8tiVs/edit?tab=t.0) · [PDF](https://drive.google.com/file/d/1oJ1uNOlKSmQMU1jxG21G12HXYeieXzjw/view?usp=sharing)
- Milestone 2 walkthrough: [video](https://www.youtube.com/watch?v=cAGsdzeKHjU)
- Milestone 2 report: [Doc](https://docs.google.com/document/d/1IRcGIQ7sCrrhUWwF40zel0zZk2Wm7761Cz6F6hUwZCQ/edit?tab=t.0) · [PDF](https://drive.google.com/file/d/1_2KZoIb3WMhVPkuZ7B3ufNoyJaOmGHek/view?usp=sharing)
- Milestone 3 walkthrough: [video](https://youtu.be/OXxA7TxO0NA)
- Milestone 3 report: [Doc](https://docs.google.com/document/d/1jKexNHYh6fhxqJKUUWuqUBDYdxrzRczK/edit?usp=sharing&ouid=116186173097911607580&rtpof=true&sd=true) · [PDF](https://drive.google.com/file/d/1ZR2wbvkXX1wDxPTpRdNd4Xg4ewnrdmNL/view?usp=sharing)

## Milestone 2 features

- Thymeleaf + Bootstrap UI for students and advisors
- Session login with BCrypt password verification (`STUDENT` / `ADVISOR` roles)
- Browse slots with provider / service / date filters and SQL `LIMIT` / `OFFSET` pagination
- Booking with `@Transactional` + `SELECT … FOR UPDATE` and partial unique index backstop
- Owner-only appointment cancellation (HTTP 403 for non-owners)
- Advisor dashboard: view bookings, publish slots, remove unbooked slots
- Automated two-thread concurrent booking test (exactly one `201`, one `409`)

## Milestone 3 features

- Mock notification service (`POST /api/notifications`) behind a coarse-grained `NotificationGateway`
- Timeout, retry, and fallback on the remote call; idempotency key so retries never send twice
- Commit first, then notify: confirmation on book and cancel, sent only after the database commit
- `GET /health` with a PostgreSQL check, plus booking and notification metrics
- INFO / WARN / ERROR logging; JSON logs in Docker
- `Dockerfile` + `docker-compose.yml` (app + PostgreSQL)
- GitHub Actions: build, all tests against PostgreSQL, and a Docker Compose health check on every push

```text
Client -> Appointment Scheduler -> Notification Service
                  |
              PostgreSQL
```

## Requirements

- Java 21
- PostgreSQL 14+ on `localhost:5432`
- Maven wrapper included (`./mvnw`)
- Docker Desktop (only for `docker compose`)

## Database setup (one time)

```bash
psql -d postgres -c "CREATE ROLE advising LOGIN PASSWORD 'advising';"
createdb -O advising advising
```

## Configuration

| Variable                 | Default                                     | Purpose                                     |
|--------------------------|---------------------------------------------|---------------------------------------------|
| `DB_URL`                 | `jdbc:postgresql://localhost:5432/advising` | JDBC URL                                    |
| `DB_USERNAME`            | `advising`                                  | Database user                               |
| `DB_PASSWORD`            | _(empty)_                                   | Database password                           |
| `APP_ENV`                | `local`                                     | Shown by `/api/status`                      |
| `NOTIFICATION_MOCK_MODE` | `UP`                                        | Mock service mode: `UP`, `DOWN`, `SLOW`     |
| `NOTIFICATION_BASE_URL`  | _(empty = this app)_                        | Where the gateway sends notifications       |

Set `DB_PASSWORD` if your PostgreSQL role requires a password.

## Build, test, run

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS tip
./mvnw clean test                                  # needs PostgreSQL; schema+seed reset on startup
./mvnw spring-boot:run                             # http://localhost:8080
```

`schema.sql` and `seed.sql` run on every startup (`DROP TABLE` + recreate) so demos and tests stay deterministic.

## Run with Docker

```bash
cp .env.example .env            # then set DB_PASSWORD
docker compose up --build       # app on http://localhost:8080, PostgreSQL inside compose
curl localhost:8080/health
docker compose down
```

The app waits for the database healthcheck before starting and restarts on failure. Logs are JSON (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`).

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

## Notification boundary

Booking and cancelling go through `AppointmentWorkflow`:

1. `AppointmentService` runs the `@Transactional` booking and commits.
2. Only then, `AppointmentNotifier` sends one `NotificationRequest` (`recipient`, `type`, `message`, `idempotencyKey`) through `HttpNotificationGateway`.

| Setting            | Value                           |
|--------------------|---------------------------------|
| Connect timeout    | 500 ms                          |
| Read timeout       | 1000 ms                         |
| Attempts           | 3 (pauses of 200 ms, 400 ms)    |
| Retried            | Timeouts, connection errors, 5xx |
| Not retried        | 4xx                             |
| Fallback           | Log ERROR, count it, booking still succeeds |

The idempotency key is `appointment-{id}-{BOOKING_CONFIRMED|BOOKING_CANCELLED}`; the mock stores each key once, so a retry after a timeout does not create a second notification.

Try an outage:

```bash
NOTIFICATION_MOCK_MODE=DOWN ./mvnw spring-boot:run   # bookings still succeed; logs show retries + fallback
curl http://localhost:8080/api/notifications         # what the mock received
```

## Health and metrics

| Path                        | Access        | Returns                                           |
|-----------------------------|---------------|---------------------------------------------------|
| `GET /health`               | Public        | `UP` / `DOWN` with a PostgreSQL check (`503` if DOWN) |
| `GET /metrics/{name}`       | Advisor login | Micrometer metric values                          |

| Metric                     | Tags                                | Use                                  |
|----------------------------|-------------------------------------|--------------------------------------|
| `advising.bookings`        | `outcome=success/conflict/failed`   | Bookings per hour, failed bookings   |
| `advising.booking.latency` | —                                   | Average latency = `TOTAL_TIME / COUNT` |
| `advising.notifications`   | `type`, `result=delivered/fallback` | Notification failures                |

```bash
curl -c advisor.txt -H 'Content-Type: application/json' \
  -d '{"email":"maria.chen@sjsu.edu","password":"password123"}' http://localhost:8080/login
curl -b advisor.txt 'http://localhost:8080/metrics/advising.bookings?tag=outcome:success'
```

The notification service is not part of `/health` on purpose: an outage there must not make the scheduler look unhealthy.

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

## Notification outage test

`BookingWhenNotificationDownTests` runs the real server with the stub returning `503`, and again with nothing listening at all. Booking still returns `201` with one `BOOKED` row, and cancel still frees the slot.

```bash
./mvnw -Dtest=BookingWhenNotificationDownTests test
```

## Continuous integration

`.github/workflows/build.yml` runs on every push and pull request:

1. **Build and test** — JDK 21, a PostgreSQL 16 service container, `./mvnw -B verify`.
2. **Docker Compose smoke test** — `docker compose up --build`, then waits for `/health` to report `UP`.

## Useful API checks

```bash
curl -i http://localhost:8080/api/status
curl -i http://localhost:8080/health
curl -i 'http://localhost:8080/api/slots?page=0&size=5'
curl -i -H 'Content-Type: application/json' \
  -d '{"email":"alex.kim@sjsu.edu","password":"password123"}' \
  -c cookies.txt http://localhost:8080/login
```
