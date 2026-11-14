CREATE TABLE match_match_type (
    match_id BIGINT NOT NULL,
    match_type VARCHAR(30) NOT NULL,
    CONSTRAINT pk_match_match_type PRIMARY KEY (match_id, match_type),
    CONSTRAINT fk_match_match_type_match FOREIGN KEY (match_id) REFERENCES match (id) ON DELETE CASCADE
);

INSERT INTO match_match_type (match_id, match_type)
SELECT id, match_type FROM match;

ALTER TABLE match DROP COLUMN match_type;
