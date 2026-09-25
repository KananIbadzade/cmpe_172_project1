# CMPE 172 Term Project: Academic Advising Scheduling System

Spring Boot backend for scheduling academic advising appointments between students and advisors.
Persistence uses Spring `JdbcTemplate` with handwritten SQL (no ORM).

- Repository: https://github.com/KananIbadzade/cmpe_172_project1
- Milestone tag: `milestone-1`
- Code walkthrough video: _add link_

## Milestone 1 scope

- PostgreSQL schema for `users`, `providers`, `services`, `availability_slots`, `appointments`
- Double-booking guard: `uq_appointment_active_slot UNIQUE (slot_id)` on `appointments`
- `schema.sql` and `seed.sql` run automatically on every startup (tables are dropped and recreated)
- Layered read path: `SlotController` -> `SlotService` -> `SlotJdbcRepository` -> PostgreSQL
- Endpoints return DTOs only

| Method | Path              | Description                                  |
|--------|-------------------|----------------------------------------------|
| GET    | `/`               | System status (`SystemStatusResponse`)       |
| GET    | `/api/slots`      | Unbooked slots ordered by start time         |
| GET    | `/api/slots/{id}` | One slot by id; `404` problem+json if absent |

## Requirements

- Java 21
- PostgreSQL 14+ running on `localhost:5432`
- Maven is optional; the included wrapper (`./mvnw`) downloads Maven 3.9.11

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

Set `DB_PASSWORD` if your PostgreSQL uses password authentication.

## Build, test, run

```bash
./mvnw clean test          # needs PostgreSQL running; tests reset the schema
./mvnw spring-boot:run     # http://localhost:8080
```

```bash
curl -i http://localhost:8080/
curl -i http://localhost:8080/api/slots
curl -i http://localhost:8080/api/slots/1
curl -i http://localhost:8080/api/slots/999
```

## Seed accounts

All seeded users share the demo password `password123` (stored as BCrypt hashes).

| Email                   | Role    |
|-------------------------|---------|
| `maria.chen@sjsu.edu`   | ADVISOR |
| `david.nguyen@sjsu.edu` | ADVISOR |
| `priya.patel@sjsu.edu`  | ADVISOR |
| `alex.kim@sjsu.edu`     | STUDENT |
| `jordan.lee@sjsu.edu`   | STUDENT |
| `sam.rivera@sjsu.edu`   | STUDENT |
