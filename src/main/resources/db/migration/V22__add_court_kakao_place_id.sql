ALTER TABLE court
    ADD COLUMN kakao_place_id VARCHAR(30),
    ADD CONSTRAINT uk_court_kakao_place_id UNIQUE (kakao_place_id);
