ALTER TABLE users
    ADD COLUMN profile_image_key VARCHAR(80),
    DROP COLUMN profile_image_url;
