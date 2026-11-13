ALTER TABLE users
    DROP COLUMN activity_latitude,
    DROP COLUMN activity_longitude,
    DROP COLUMN activity_radius_km,
    DROP COLUMN tournament_history;

CREATE TABLE user_activity_areas (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    area_id BIGINT NOT NULL REFERENCES areas(id),
    PRIMARY KEY (user_id, area_id)
);

CREATE INDEX ix_user_activity_areas_area_id ON user_activity_areas (area_id);
