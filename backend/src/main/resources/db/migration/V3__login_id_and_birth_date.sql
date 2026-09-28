-- 로그인은 아이디로 하고, 이메일은 선택 입력으로 바꾼다
ALTER TABLE users ADD COLUMN login_id VARCHAR(30);

-- 기존 사용자: 이메일 앞부분을 아이디로 옮긴다 (demo01@fitmate.com → demo01)
UPDATE users
SET login_id = left(lower(regexp_replace(split_part(email, '@', 1), '[^A-Za-z0-9_]', '_', 'g')), 20);

-- 겹치거나 너무 짧으면 뒤에 사용자 ID를 붙여 유일하게 만든다
UPDATE users u
SET login_id = u.login_id || '_' || u.id
WHERE length(u.login_id) < 4
   OR EXISTS (SELECT 1 FROM users o WHERE o.login_id = u.login_id AND o.id < u.id);

ALTER TABLE users ALTER COLUMN login_id SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_login_id_key UNIQUE (login_id);

ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- 출생 연도 대신 생년월일 (기존 값은 1월 1일로 옮긴다)
ALTER TABLE users ADD COLUMN birth_date DATE;
UPDATE users SET birth_date = make_date(birth_year, 1, 1) WHERE birth_year IS NOT NULL;
ALTER TABLE users DROP COLUMN birth_year;
