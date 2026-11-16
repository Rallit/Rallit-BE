ALTER TABLE match
    DROP CONSTRAINT ck_match_min_ntrp,
    DROP CONSTRAINT ck_match_max_ntrp,
    ADD CONSTRAINT ck_match_min_ntrp CHECK (min_ntrp BETWEEN 1.0 AND 4.0),
    ADD CONSTRAINT ck_match_max_ntrp CHECK (max_ntrp BETWEEN 1.0 AND 4.0);
