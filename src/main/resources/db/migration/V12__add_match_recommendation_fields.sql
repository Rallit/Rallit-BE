ALTER TABLE areas
    ADD COLUMN center_latitude NUMERIC(9, 6),
    ADD COLUMN center_longitude NUMERIC(9, 6),
    ADD CONSTRAINT ck_areas_center_pair CHECK (
        (center_latitude IS NULL AND center_longitude IS NULL)
        OR (center_latitude IS NOT NULL AND center_longitude IS NOT NULL
            AND center_latitude BETWEEN -90 AND 90 AND center_longitude BETWEEN -180 AND 180)
    );

-- Link existing courts only after verifying their registered district.
ALTER TABLE court
    ADD COLUMN area_id BIGINT REFERENCES areas(id);

ALTER TABLE match
    ADD COLUMN same_gender_only BOOLEAN NOT NULL DEFAULT FALSE;
