-- 차단: 차단하면 추천·매칭 요청·채팅에서 서로 보이지 않거나 주고받을 수 없다
CREATE TABLE user_blocks (
    blocker_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
-- "나를 차단한 사람" 조회용 (PK는 blocker_id가 앞이라 이 방향은 따로 인덱스가 필요)
CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id);

-- 신고: 운영자가 검토할 수 있게 저장한다. 신고당한 사람이 탈퇴해도 신고 기록은 남긴다
CREATE TABLE user_reports (
    id            BIGSERIAL PRIMARY KEY,
    reporter_id   BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    reported_id   BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    reason        VARCHAR(20)  NOT NULL, -- SPAM / ABUSE / SEXUAL / FAKE_PROFILE / NO_SHOW / OTHER
    detail        VARCHAR(500),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING / RESOLVED / DISMISSED
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 같은 사람을 검토 대기 중에 중복 신고하지 못하게 한다
CREATE UNIQUE INDEX uq_user_reports_pending
    ON user_reports (reporter_id, reported_id) WHERE status = 'PENDING';
CREATE INDEX idx_user_reports_reported ON user_reports (reported_id, status);
