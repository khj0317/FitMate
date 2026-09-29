-- 관리자와 이용 정지
ALTER TABLE users ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER'; -- USER / ADMIN
ALTER TABLE users ADD COLUMN suspended_until TIMESTAMPTZ;             -- 이 시각까지 로그인 불가 (영구 정지는 먼 미래)

-- 신고 처리 기록
ALTER TABLE user_reports ADD COLUMN action VARCHAR(20);                -- DISMISS / WARN / SUSPEND_7D / SUSPEND_PERMANENT
ALTER TABLE user_reports ADD COLUMN admin_note VARCHAR(500);
ALTER TABLE user_reports ADD COLUMN resolved_by BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE user_reports ADD COLUMN resolved_at TIMESTAMPTZ;
CREATE INDEX idx_user_reports_status ON user_reports (status, id DESC);

-- 관리자가 숨긴 글은 피드·상세에서 보이지 않는다 (지우지 않고 남겨서 되돌릴 수 있게)
ALTER TABLE posts ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
