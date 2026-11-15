-- The application's top NTRP band, 4.0+, is stored as 4.0.
UPDATE match
SET min_ntrp = CASE WHEN min_ntrp > 4.0 THEN 4.0 ELSE min_ntrp END,
    max_ntrp = CASE WHEN max_ntrp > 4.0 THEN 4.0 ELSE max_ntrp END
WHERE min_ntrp > 4.0 OR max_ntrp > 4.0;
