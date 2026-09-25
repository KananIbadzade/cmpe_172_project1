-- CMPE 172 Term Project Milestone 1
-- Seed data: Academic Advising Scheduling System
-- IDs are deterministic because schema.sql recreates every table (and its sequence) on startup.
-- Every seeded account uses the demo password "password123" (BCrypt, cost 10).

INSERT INTO users (email, password_hash, full_name, role) VALUES
    ('maria.chen@sjsu.edu',   '$2a$10$VjVWT4GCpyLo7uffBYJCNuTaUU2hRAKrP/rEweUDdopFBwTOQH.zS', 'Maria Chen',   'ADVISOR'),
    ('david.nguyen@sjsu.edu', '$2a$10$bbykHGnWFJNd3S9t.VnzRuqBZVk94m760UyAWu2erZMUD2zMe7I92', 'David Nguyen', 'ADVISOR'),
    ('priya.patel@sjsu.edu',  '$2a$10$gnW4FK5Ngxi3CVAD.4EI..4LouxTehwiVyc0D.k8LhhVEv3mhqNMu', 'Priya Patel',  'ADVISOR'),
    ('alex.kim@sjsu.edu',     '$2a$10$AXwXO6eHpfnAlb4Un9jdAeNm9tni5.UPfvPeJzpWwcMHGo8YX4fNO', 'Alex Kim',     'STUDENT'),
    ('jordan.lee@sjsu.edu',   '$2a$10$c4Ud4aUJKSc97oA4LEixEOuyYr3y3.kz/3dViZzqG6cSlAshiG1/e', 'Jordan Lee',   'STUDENT'),
    ('sam.rivera@sjsu.edu',   '$2a$10$awIXMODSZSCEsUDQ2yWKOOAJrPOyTCjTeO28PPq/itkmcBz/RZzju', 'Sam Rivera',   'STUDENT');

INSERT INTO providers (user_id, department, office_location) VALUES
    (1, 'Computer Engineering',          'ENG 281'),
    (2, 'Software Engineering',          'ENG 285'),
    (3, 'Undergraduate Advising Center', 'CL 118');

INSERT INTO services (name, description, duration_minutes, price) VALUES
    ('Graduation Check',          'Audit of remaining major and GE requirements before applying to graduate.', 30, 0.00),
    ('Major Declaration',         'Declare or change a major and review the required course roadmap.',         30, 0.00),
    ('Academic Probation Review', 'Build a recovery plan for students below a 2.0 GPA.',                        45, 0.00),
    ('Course Planning',           'Plan next semester''s schedule and prerequisite sequence.',                30, 0.00);

-- Relative dates keep seeded slots in the future whenever the app starts.
INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_booked) VALUES
    (1, 1, (CURRENT_DATE + 1) + TIME '09:00', (CURRENT_DATE + 1) + TIME '09:30', TRUE),
    (1, 4, (CURRENT_DATE + 1) + TIME '10:00', (CURRENT_DATE + 1) + TIME '10:30', FALSE),
    (1, 1, (CURRENT_DATE + 2) + TIME '09:00', (CURRENT_DATE + 2) + TIME '09:30', FALSE),
    (2, 2, (CURRENT_DATE + 1) + TIME '13:00', (CURRENT_DATE + 1) + TIME '13:30', FALSE),
    (2, 4, (CURRENT_DATE + 2) + TIME '14:00', (CURRENT_DATE + 2) + TIME '14:30', TRUE),
    (2, 2, (CURRENT_DATE + 3) + TIME '11:00', (CURRENT_DATE + 3) + TIME '11:30', FALSE),
    (3, 3, (CURRENT_DATE + 1) + TIME '15:00', (CURRENT_DATE + 1) + TIME '15:45', FALSE),
    (3, 3, (CURRENT_DATE + 3) + TIME '15:00', (CURRENT_DATE + 3) + TIME '15:45', FALSE);

INSERT INTO appointments (customer_id, provider_id, slot_id, service_id, status, notes) VALUES
    (4, 1, 1, 1, 'BOOKED', 'Confirm remaining GE areas before filing for graduation.'),
    (5, 2, 5, 4, 'BOOKED', 'Plan upper-division CMPE prerequisites for next semester.');
