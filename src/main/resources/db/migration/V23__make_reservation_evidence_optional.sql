ALTER TABLE court_slot RENAME COLUMN status TO evidence_review_status;

-- Rejected evidence cancels the match and releases its time slot for a new reservation.
ALTER TABLE court_slot
    ALTER COLUMN evidence_file_key DROP NOT NULL,
    DROP COLUMN rental_fee,
    DROP CONSTRAINT ex_court_slot_overlap,
    ADD CONSTRAINT ex_court_slot_overlap EXCLUDE USING gist (
        court_id WITH =,
        court_number WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (evidence_review_status <> 'REJECTED'),
    ADD CONSTRAINT ck_court_slot_evidence_review CHECK (
        (evidence_file_key IS NULL) = (evidence_review_status = 'NOT_SUBMITTED')
    );

CREATE INDEX idx_court_slot_evidence_review_status_created_at ON court_slot (evidence_review_status, created_at);

UPDATE match m
SET status = 'CANCELED', canceled_at = COALESCE(s.reviewed_at, now()), cancel_reason = s.rejection_reason
FROM court_slot s
WHERE s.id = m.court_slot_id AND s.evidence_review_status = 'REJECTED' AND m.status IN ('PENDING_APPROVAL', 'RECRUITING');

UPDATE match SET status = 'RECRUITING' WHERE status = 'PENDING_APPROVAL';

ALTER TABLE image_upload
    ALTER COLUMN purpose TYPE VARCHAR(30),
    DROP CONSTRAINT ck_image_upload_purpose,
    ADD CONSTRAINT ck_image_upload_purpose CHECK (purpose IN ('PROFILE', 'COURT', 'RESERVATION_EVIDENCE'));
