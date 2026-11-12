ALTER TABLE users DROP CONSTRAINT uk_users_nickname;
CREATE UNIQUE INDEX uk_users_nickname ON users (lower(nickname));
