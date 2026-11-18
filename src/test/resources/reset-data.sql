-- Test-only: empty every table and restart ID sequences so V2__seed_demo_data.sql
-- produces the same IDs before each test. The schema itself is left to Flyway.
TRUNCATE appointments, availability_slots, services, providers, users RESTART IDENTITY CASCADE;
