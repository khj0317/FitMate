-- 매너 평가: 끝난 모임의 참가자끼리, 또는 1:1 매칭 상대끼리 평가한다
CREATE TABLE manner_reviews (
    id                BIGSERIAL PRIMARY KEY,
    reviewer_id       BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    target_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gathering_id      BIGINT       REFERENCES gatherings(id) ON DELETE SET NULL,
    match_request_id  BIGINT       REFERENCES match_requests(id) ON DELETE SET NULL,
    rating            VARCHAR(10)  NOT NULL, -- GOOD / NORMAL / BAD
    tags              TEXT[]       NOT NULL DEFAULT '{}',
    score_delta       NUMERIC(3,1) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (reviewer_id <> target_id)
);
-- 같은 모임(또는 같은 매칭)에서 같은 사람을 두 번 평가할 수 없다
CREATE UNIQUE INDEX uq_manner_reviews_gathering
    ON manner_reviews (reviewer_id, target_id, gathering_id) WHERE gathering_id IS NOT NULL;
CREATE UNIQUE INDEX uq_manner_reviews_match
    ON manner_reviews (reviewer_id, target_id, match_request_id) WHERE match_request_id IS NOT NULL;
CREATE INDEX idx_manner_reviews_target ON manner_reviews (target_id);

-- 매너 온도는 0.0 ~ 99.9
ALTER TABLE users ADD CONSTRAINT chk_users_manner_score CHECK (manner_score BETWEEN 0 AND 99.9);

-- 알림
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    body        VARCHAR(300),
    link        VARCHAR(200),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, id DESC);
-- 안 읽은 알림 수를 자주 세므로 부분 인덱스
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

-- 모임 목록은 "앞으로 열리는 모임"을 시간순으로 본다
CREATE INDEX idx_gathering_participants_status ON gathering_participants (user_id, status);
