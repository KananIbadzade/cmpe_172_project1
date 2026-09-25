-- CMPE 172 Term Project Milestone 1
-- Schema DDL: Academic Advising Scheduling System
-- Target: PostgreSQL 14+

DROP TABLE IF EXISTS appointments CASCADE;
DROP TABLE IF EXISTS availability_slots CASCADE;
DROP TABLE IF EXISTS services CASCADE;
DROP TABLE IF EXISTS providers CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 1. Users (student and advisor login identities)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'ADVISOR')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Providers (advisor profile, 1-to-1 with users)
CREATE TABLE providers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    department VARCHAR(100) NOT NULL,
    office_location VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Services (advising session types)
CREATE TABLE services (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
    price NUMERIC(8, 2) NOT NULL DEFAULT 0.00 CHECK (price >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Availability slots (time blocks published by advisors)
CREATE TABLE availability_slots (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES services(id) ON DELETE RESTRICT,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    is_booked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_time_window CHECK (end_time > start_time),
    CONSTRAINT uq_provider_slot UNIQUE (provider_id, start_time)
);

-- 5. Appointments (student reservations)
CREATE TABLE appointments (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id BIGINT NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    slot_id BIGINT NOT NULL REFERENCES availability_slots(id) ON DELETE RESTRICT,
    service_id BIGINT NOT NULL REFERENCES services(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CANCELLED')),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Double-booking guard: at most one appointment row per slot
    CONSTRAINT uq_appointment_active_slot UNIQUE (slot_id)
);

-- uq_provider_slot already indexes (provider_id, start_time).
CREATE INDEX idx_slots_available_start ON availability_slots(start_time) WHERE is_booked = FALSE;
CREATE INDEX idx_appointments_customer ON appointments(customer_id);
