-- Existing values were written as Asia/Seoul wall-clock times, so convert them explicitly regardless of the session time zone.
ALTER TABLE court_slot DROP CONSTRAINT ex_court_slot_overlap;

ALTER TABLE users
    ALTER COLUMN withdrawn_at TYPE TIMESTAMP(6) WITH TIME ZONE USING withdrawn_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE social_account
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE court
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE court_slot
    ALTER COLUMN starts_at TYPE TIMESTAMP(6) WITH TIME ZONE USING starts_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN ends_at TYPE TIMESTAMP(6) WITH TIME ZONE USING ends_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN reviewed_at TYPE TIMESTAMP(6) WITH TIME ZONE USING reviewed_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE match
    ALTER COLUMN recruitment_deadline TYPE TIMESTAMP(6) WITH TIME ZONE
        USING recruitment_deadline AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN confirmed_at TYPE TIMESTAMP(6) WITH TIME ZONE USING confirmed_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN completed_at TYPE TIMESTAMP(6) WITH TIME ZONE USING completed_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN canceled_at TYPE TIMESTAMP(6) WITH TIME ZONE USING canceled_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE match_application
    ALTER COLUMN refund_policy_agreed_at TYPE TIMESTAMP(6) WITH TIME ZONE
        USING refund_policy_agreed_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN accepted_at TYPE TIMESTAMP(6) WITH TIME ZONE USING accepted_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN payment_due_at TYPE TIMESTAMP(6) WITH TIME ZONE USING payment_due_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN canceled_at TYPE TIMESTAMP(6) WITH TIME ZONE USING canceled_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE payment
    ALTER COLUMN approved_at TYPE TIMESTAMP(6) WITH TIME ZONE USING approved_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN processing_started_at TYPE TIMESTAMP(6) WITH TIME ZONE
        USING processing_started_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE payment_refund
    ALTER COLUMN canceled_at TYPE TIMESTAMP(6) WITH TIME ZONE USING canceled_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN last_requested_at TYPE TIMESTAMP(6) WITH TIME ZONE USING last_requested_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE settlement
    ALTER COLUMN settled_at TYPE TIMESTAMP(6) WITH TIME ZONE USING settled_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE notification
    ALTER COLUMN read_at TYPE TIMESTAMP(6) WITH TIME ZONE USING read_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE match_request
    ALTER COLUMN expires_at TYPE TIMESTAMP(6) WITH TIME ZONE USING expires_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN deleted_at TYPE TIMESTAMP(6) WITH TIME ZONE USING deleted_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'Asia/Seoul',
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE USING updated_at AT TIME ZONE 'Asia/Seoul';

ALTER TABLE court_slot
    ADD CONSTRAINT ex_court_slot_overlap EXCLUDE USING gist (
        court_id WITH =,
        court_number WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status <> 'REJECTED');
