ALTER TABLE users
    ADD COLUMN tennis_started_on DATE,
    ADD CONSTRAINT ck_users_tennis_started_on CHECK (EXTRACT(DAY FROM tennis_started_on) = 1),
    DROP COLUMN experience_period;
