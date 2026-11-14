-- Supply rallit.demo_date in the current transaction before executing this block.
DO $seed$
DECLARE
    demo_date date := current_setting('rallit.demo_date')::date;
    seoul_now timestamp := statement_timestamp() AT TIME ZONE 'Asia/Seoul';
    demo_host_id bigint;
    demo_court_id bigint;
    demo_slot_id bigint;
    demo_match_id bigint;
    demo record;
BEGIN
    IF demo_date <= seoul_now::date THEN
        RAISE EXCEPTION 'Choose a demo date after today in Asia/Seoul';
    END IF;

    PERFORM pg_advisory_xact_lock(210021);

    INSERT INTO users (role, status, nickname, email, gender, age_group, experience_period, ntrp,
                       same_gender_only, created_at, updated_at)
    VALUES ('USER', 'ACTIVE', '랠릿조회데모호스트', 'match-discovery-host@rallit.invalid', 'MALE',
            'TWENTIES', 'ONE_TO_THREE_YEARS', 3.0, false, seoul_now, seoul_now)
    ON CONFLICT ON CONSTRAINT uk_users_email DO NOTHING;

    SELECT id INTO STRICT demo_host_id FROM users
    WHERE email_key = 'match-discovery-host@rallit.invalid'
      AND nickname = '랠릿조회데모호스트' AND role = 'USER' AND status = 'ACTIVE' AND gender = 'MALE';

    FOR demo IN
        SELECT * FROM (VALUES
            ('singles', '랠릿 데모 마포 코트', '서울특별시 마포구 (가상 코트)', 37.555000, 126.910000,
             TIME '10:00', 'SINGLES', 0, 1, 2.5, 3.5, 8000),
            ('rally', '랠릿 데모 마포 코트', '서울특별시 마포구 (가상 코트)', 37.555000, 126.910000,
             TIME '13:00', 'RALLY', 1, 1, 2.0, 4.0, 5000),
            ('mixed-doubles', '랠릿 데모 서대문 코트', '서울특별시 서대문구 (가상 코트)', 37.574000, 126.936000,
             TIME '18:00', 'MIXED_DOUBLES', 1, 2, 3.0, 4.0, 12000)
        ) AS sample(seed_key, court_name, court_address, latitude, longitude, starts,
                    match_type, male_count, female_count, min_ntrp, max_ntrp, fee)
    LOOP
        SELECT id INTO demo_court_id FROM court
        WHERE name = demo.court_name AND address = demo.court_address
          AND facility_notes = '랠릿 로컬 매치 조회 데모'
        ORDER BY id LIMIT 1;

        IF demo_court_id IS NULL THEN
            INSERT INTO court (name, address, latitude, longitude, has_parking, has_shower,
                               facility_notes, created_at, updated_at)
            VALUES (demo.court_name, demo.court_address, demo.latitude, demo.longitude, true, false,
                    '랠릿 로컬 매치 조회 데모', seoul_now, seoul_now)
            RETURNING id INTO demo_court_id;
        END IF;

        SELECT id INTO demo_slot_id FROM court_slot
        WHERE court_id = demo_court_id AND registrant_user_id = demo_host_id
          AND evidence_file_key = 'local-demo/21/' || demo_date || '/' || demo.seed_key;

        IF demo_slot_id IS NULL THEN
            INSERT INTO court_slot (court_id, registrant_user_id, court_number, starts_at, ends_at,
                                    rental_fee, evidence_file_key, status, reviewed_at, created_at, updated_at)
            VALUES (demo_court_id, demo_host_id, '1', demo_date + demo.starts,
                    demo_date + demo.starts + interval '2 hours', demo.fee,
                    'local-demo/21/' || demo_date || '/' || demo.seed_key,
                    'APPROVED', seoul_now, seoul_now, seoul_now)
            RETURNING id INTO demo_slot_id;
        END IF;

        INSERT INTO match (court_slot_id, host_user_id, status, male_recruit_count, female_recruit_count,
                           min_ntrp, max_ntrp, participation_fee, recruitment_deadline, created_at, updated_at)
        VALUES (demo_slot_id, demo_host_id, 'RECRUITING', demo.male_count, demo.female_count,
                demo.min_ntrp, demo.max_ntrp, demo.fee,
                demo_date + demo.starts - interval '1 hour', seoul_now, seoul_now)
        ON CONFLICT (court_slot_id) DO NOTHING;

        SELECT id INTO STRICT demo_match_id FROM match
        WHERE court_slot_id = demo_slot_id AND host_user_id = demo_host_id;
        INSERT INTO match_match_type (match_id, match_type)
        VALUES (demo_match_id, demo.match_type)
        ON CONFLICT DO NOTHING;
    END LOOP;
END
$seed$;
