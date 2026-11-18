-- Milestone 4: the reminder agent stamps an appointment once its reminder was delivered,
-- so each appointment is reminded at most once.
ALTER TABLE appointments ADD COLUMN reminder_sent_at TIMESTAMP WITH TIME ZONE;
