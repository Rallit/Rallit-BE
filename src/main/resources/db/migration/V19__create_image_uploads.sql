CREATE TABLE image_upload (
    id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    target_id BIGINT NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    content_size BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_image_upload_purpose CHECK (purpose IN ('PROFILE', 'COURT')),
    CONSTRAINT ck_image_upload_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_image_upload_size CHECK (content_size BETWEEN 1 AND 5242880)
);
