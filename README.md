# CMPE 172 Term Project: Academic Advising Scheduling System

[![build](https://github.com/KananIbadzade/cmpe_172_project1/actions/workflows/build.yml/badge.svg)](https://github.com/KananIbadzade/cmpe_172_project1/actions/workflows/build.yml)

Spring Boot app for scheduling academic advising appointments between students and advisors.
Persistence uses Spring `JdbcTemplate` with handwritten SQL (no ORM / JPA).

- Repository: https://github.com/KananIbadzade/cmpe_172_project1
- Milestone tags: `milestone-1`, `milestone-2`, `milestone-3`, `milestone-4`
- Milestone 1 walkthrough: [video](https://youtu.be/V18AfV5O15g)
- Milestone 1 report: [Doc](https://docs.google.com/document/d/1kcixdiBthgPmq50JXbeE8OWpyh0KBzus3u_8eY8tiVs/edit?tab=t.0) · [PDF](https://drive.google.com/file/d/1oJ1uNOlKSmQMU1jxG21G12HXYeieXzjw/view?usp=sharing)
- Milestone 2 walkthrough: [video](https://www.youtube.com/watch?v=cAGsdzeKHjU)
- Milestone 2 report: [Doc](https://docs.google.com/document/d/1IRcGIQ7sCrrhUWwF40zel0zZk2Wm7761Cz6F6hUwZCQ/edit?tab=t.0) · [PDF](https://drive.google.com/file/d/1_2KZoIb3WMhVPkuZ7B3ufNoyJaOmGHek/view?usp=sharing)
- Milestone 3 walkthrough: [video](https://youtu.be/OXxA7TxO0NA)
- Milestone 3 report: [Doc](https://docs.google.com/document/d/1jKexNHYh6fhxqJKUUWuqUBDYdxrzRczK/edit?usp=sharing&ouid=116186173097911607580&rtpof=true&sd=true) · [PDF](https://drive.google.com/file/d/1ZR2wbvkXX1wDxPTpRdNd4Xg4ewnrdmNL/view?usp=sharing)
- Milestone 4 walkthrough: [video](https://www.youtube.com/watch?v=xCh09XuahaY)
- Milestone 4 report: [Doc](https://docs.google.com/document/d/1aIWuXkjZe_Ht3bDzripJgnTrzs8CorCjZMVejbV0hZE/edit?usp=sharing) · [PDF](https://drive.google.com/file/d/1OQUQ1PG3H2NPQceVjWOJtn_s6PVjfrO9/view?usp=sharing)

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

## Milestone 4 features (enterprise AI)

Built with Spring AI and the Gemini API (free Google AI Studio key). Everything below needs a `GEMINI_API_KEY`;
the rest of the app, the tests and CI run without one.

- **Chat assistant** (`/assistant` page, `POST /api/student/chat`): a tool-calling agent that finds open slots, lists
  your appointments, proposes bookings and cancellations, and answers policy questions. It handles multi-step requests
  ("find the earliest slot this week and book it") and multi-turn conversations, and acts as the logged-in student.
- **Confirm-before-write, enforced in code:** the model can only *propose* a booking or cancellation. The server
  commits it when your next message is a plain "yes", through `AppointmentWorkflow`, so the transaction, the row lock,
  the unique index and the confirmation notification all still apply. A booking can never be committed in the same
  turn it was proposed, only your very next message can confirm it, and the model is not involved in the commit. If the slot was just taken, you get alternatives.
- **Reminder agent** (autonomous): every 15 minutes it finds booked appointments starting soon, has the LLM write a
  personalized reminder, and sends it through the notification gateway (idempotency key
  `appointment-{id}-APPOINTMENT_REMINDER`). An appointment is marked reminded only after delivery. If the LLM is
  unavailable, a template message is sent instead.
- **Policy Q&A (RAG):** `knowledge/advising-policies.md` is split by section, embedded, and kept in an in-memory
  `SimpleVectorStore`. `POST /api/assistant/ask` answers only from the retrieved text and lists its sources.
- **Saved chat history:** each student has one conversation stored in PostgreSQL (`spring_ai_chat_memory`, last 20
  messages), so it survives logout and server restarts.

```text
Browser /assistant ──> ChatController ──> ChatService ──> Gemini (tools: AdvisingTools)
                                              │  "yes" ──> AppointmentWorkflow ──> PostgreSQL ──> Notification Service
ReminderAgent (@Scheduled / manual) ──> Gemini ──> NotificationGateway ──> Notification Service
KnowledgeBase (SimpleVectorStore) <── advising-policies.md ;  chat memory ──> PostgreSQL
```

### AI endpoints

| Endpoint                                       | Who     | Purpose                                              |
|------------------------------------------------|---------|------------------------------------------------------|
| `GET /assistant`                               | Student | Chat page with saved history and a policy box        |
| `POST /api/student/chat` `{"message": "..."}`  | Student | One chat turn → `{reply, pendingAction}`             |
| `GET /api/student/chat/history`                | Student | Saved messages, oldest first                         |
| `GET /api/student/chat/ping`                   | Student | Smoke test: one prompt to Gemini                     |
| `POST /api/assistant/ask` `{"question": "..."}`| Public  | Policy Q&A → `{answer, sources}`                     |
| `POST /api/advisor/agents/reminders/run`       | Advisor | Run the reminder agent now → `{scanned, sent, failed}` |

Without a key, the AI endpoints return `503` "AI assistant is not configured"; if Gemini itself fails (quota, network), they return `503` "temporarily unavailable".

### Demo: reminder agent

The seeded appointments start one to two days out, and the scheduled job would otherwise send them within 15 minutes
of startup. For a demo, turn the schedule off and trigger the agent by hand with a wider window:

```bash
REMINDERS_ENABLED=false ./mvnw spring-boot:run
curl -c adv.txt -H 'Content-Type: application/json' \
  -d '{"email":"maria.chen@sjsu.edu","password":"password123"}' localhost:8080/login
curl -b adv.txt -X POST 'localhost:8080/api/advisor/agents/reminders/run?windowHours=72'   # {"scanned":2,"sent":2,"failed":0}
curl localhost:8080/api/notifications                                                       # the reminders the mock service received
curl -b adv.txt localhost:8080/metrics/advising.reminder.messages                           # source=llm: written by Gemini
curl -b adv.txt -X POST 'localhost:8080/api/advisor/agents/reminders/run?windowHours=72'   # sent 0: already reminded
# to run it again:
psql -U advising -d advising -c "UPDATE appointments SET reminder_sent_at = NULL"
```

### Demo: chat agent

Open `http://localhost:8080/assistant` as `sam.rivera@sjsu.edu`, or use curl:

```bash
curl -c s.txt -H 'Content-Type: application/json' -d '{"email":"sam.rivera@sjsu.edu","password":"password123"}' localhost:8080/login
curl -b s.txt -H 'Content-Type: application/json' -d '{"message":"Find the earliest free slot and book it"}' localhost:8080/api/student/chat
curl -b s.txt localhost:8080/api/student/appointments        # still empty: it only proposed
curl -b s.txt -H 'Content-Type: application/json' -d '{"message":"yes"}' localhost:8080/api/student/chat   # now it books
```

### AI tests

All run without a key and without network: a stub `ChatModel` and a fake `EmbeddingModel` stand in for Gemini.

| Test class                       | What it proves                                                                          |
|----------------------------------|-----------------------------------------------------------------------------------------|
| `AgentConcurrentBookingTests`    | Two students confirm the same slot at once through the agent: one booking, one "taken" |
| `AgentBookingFlowTests`          | Proposals write nothing; "yes" books through the workflow; cancel is owner-only         |
| `AgentSafetyTests`               | No pending / expired / someone else's proposal is never committed                       |
| `ChatServiceLlmFlowTests`        | The real tool-calling pipeline passes the student id to the tools                       |
| `ChatHistoryTests`               | History is stored per student in PostgreSQL, survives a new service instance, is capped |
| `KnowledgeBaseTests`, `AssistantAskTests` | Ingestion, retrieval, grounding, sources                                       |
| `ReminderAgentTests`, `ReminderFallbackTests`, `ReminderDeliveryFailureTests` | Reminders sent once, template fallback, retry on failure |
| `AiPingTests`, `AssistantPageTests` | 503 without a key, access control, the `/assistant` page                              |

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
| `GEMINI_API_KEY`         | _(empty = AI off)_                          | Free Google AI Studio key for the AI features |
| `GEMINI_CHAT_MODEL`      | `gemini-3.8-flash`                          | Gemini chat model                           |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-001`                      | Gemini embedding model                      |
| `REMINDERS_ENABLED`      | `true`                                      | Run the reminder job on its schedule        |
| `REMINDERS_CRON`         | `0 */15 * * * *`                            | Reminder job schedule (every 15 minutes)    |
| `REMINDER_WINDOW_HOURS`  | `24`                                        | Remind appointments starting within this many hours |

Set `DB_PASSWORD` if your PostgreSQL role requires a password.

## AI setup (Milestone 4)

The AI features use Spring AI with the Gemini Developer API.

1. Create a free key at [Google AI Studio](https://aistudio.google.com/apikey).
2. Put it in a `.env` file in the repo root, or export it in your shell. Never commit it (`.env` is git-ignored):
   ```bash
   echo 'GEMINI_API_KEY=your-key' > .env      # read automatically by ./mvnw spring-boot:run and docker compose
   # or: export GEMINI_API_KEY=your-key
   ./mvnw spring-boot:run
   ```
   A real environment variable overrides the `.env` file. Any other variable in `.env` (for example `DB_PASSWORD`)
   applies to local runs too.
3. Log in as a student and call the smoke endpoint; it returns a model reply and the model name:
   ```bash
   curl -c s.txt -H 'Content-Type: application/json' \
     -d '{"email":"sam.rivera@sjsu.edu","password":"password123"}' localhost:8080/login
   curl -b s.txt localhost:8080/api/student/chat/ping
   ```

Model names change over time. If Gemini rejects the default, pick a current one from the
[models page](https://ai.google.dev/gemini-api/docs/models) and set `GEMINI_CHAT_MODEL`.
Docker passes only `GEMINI_API_KEY` and `REMINDERS_ENABLED`; set the model variables for local runs.

**Without a key the app still works.** Spring AI's Gemini auto-configuration is switched off
(`ai/AiKeyEnvironmentPostProcessor`), tests and CI never call Gemini, and AI endpoints return
`503` "AI assistant is not configured".

## Build, test, run

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS tip
./mvnw clean test                                  # needs PostgreSQL; tests truncate + reseed the tables
./mvnw spring-boot:run                             # http://localhost:8080
```

### Database migrations (Flyway)

The schema is managed by Flyway migrations in `src/main/resources/db/migration`:

| Migration                 | Purpose                    |
|---------------------------|----------------------------|
| `V1__create_schema.sql`   | Tables, constraints, indexes |
| `V2__seed_demo_data.sql`  | Demo users, slots, bookings |
| `V3__add_reminder_sent_at.sql` | `appointments.reminder_sent_at` for the reminder agent |
| `V4__chat_memory.sql`     | `spring_ai_chat_memory` table for saved chat history |

Each migration runs once and is recorded in `flyway_schema_history`; later startups apply only new migrations, so **bookings survive restarts**. Schema changes go in a new `V5__…` file; never edit an applied migration.

Tests share the same database and reset it before each test with `src/test/resources/reset-data.sql` (`TRUNCATE … RESTART IDENTITY`) followed by the seed migration. Don't point tests at a database whose data you need.

Upgrading a local database created before Milestone 4 (tables exist but no Flyway history), one time:

```bash
psql -U advising -d advising -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

## Run with Docker

```bash
cp .env.example .env            # then set DB_PASSWORD
docker compose up --build       # app on http://localhost:8080, PostgreSQL inside compose
curl localhost:8080/health
docker compose down             # data is kept in the named volume `pgdata`
docker compose down -v          # also deletes the volume (fresh database next time)
```

PostgreSQL data lives in the named volume `pgdata`, so bookings survive `docker compose down` / `up` and container restarts. CI proves it: it books a slot, restarts the stack, and checks the booking is still there. The app waits for the database healthcheck before starting and restarts on failure. Logs are JSON (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`).

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
| `/assistant`         | Student    | AI chat with saved history + policy Q&A      |

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

All accounts use password `password123` (BCrypt hashes in `V2__seed_demo_data.sql`).

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
