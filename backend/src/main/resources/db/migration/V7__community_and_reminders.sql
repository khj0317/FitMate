-- 커뮤니티: 동네 운동 게시판
CREATE TABLE posts (
    id             BIGSERIAL PRIMARY KEY,
    author_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category       VARCHAR(20)  NOT NULL, -- CERTIFY / QUESTION / REVIEW / FREE
    sport_id       SMALLINT     REFERENCES sports(id),
    content        TEXT         NOT NULL,
    -- 글을 쓸 때의 활동 지역. "우리 동네 글" 반경 검색에 쓰고, 화면에는 동 이름만 보여준다
    location       geography(Point, 4326),
    area_name      VARCHAR(100),
    like_count     INT          NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    comment_count  INT          NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX idx_posts_location ON posts USING GIST (location);
CREATE INDEX idx_posts_author ON posts (author_id, id DESC);
CREATE INDEX idx_posts_category ON posts (category, id DESC);

CREATE TABLE post_images (
    id          BIGSERIAL PRIMARY KEY,
    post_id     BIGINT       NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL,
    width       INT          NOT NULL,
    height      INT          NOT NULL,
    sort_order  SMALLINT     NOT NULL
);
CREATE INDEX idx_post_images_post ON post_images (post_id, sort_order);

-- 좋아요는 (글, 사람) 한 번만. 수는 posts.like_count에 원자적으로 더하고 뺀다
CREATE TABLE post_likes (
    post_id     BIGINT       NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_post_likes_user ON post_likes (user_id);

-- 댓글은 한 단계 답글까지. 답글이 달린 댓글을 지우면 내용만 지우고 자리는 남긴다
CREATE TABLE comments (
    id          BIGSERIAL PRIMARY KEY,
    post_id     BIGINT         NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id   BIGINT         REFERENCES users(id) ON DELETE SET NULL,
    parent_id   BIGINT         REFERENCES comments(id) ON DELETE CASCADE,
    content     VARCHAR(1000),
    deleted     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_comments_post ON comments (post_id, id);
CREATE INDEX idx_comments_parent ON comments (parent_id) WHERE parent_id IS NOT NULL;

-- 모임 리마인더(시작 1시간 전)와 평가 요청(끝난 뒤) 알림을 한 번만 보내기 위한 기록
ALTER TABLE gatherings ADD COLUMN reminder_sent_at TIMESTAMPTZ;
ALTER TABLE gatherings ADD COLUMN review_prompt_sent_at TIMESTAMPTZ;
CREATE INDEX idx_gatherings_reminder ON gatherings (starts_at)
    WHERE reminder_sent_at IS NULL AND status IN ('RECRUITING', 'CLOSED');
CREATE INDEX idx_gatherings_review_prompt ON gatherings (starts_at)
    WHERE review_prompt_sent_at IS NULL AND status <> 'CANCELED';

-- 끈 알림 종류 (MATCH / GATHERING / MANNER / COMMUNITY)
ALTER TABLE users ADD COLUMN muted_notification_types TEXT[] NOT NULL DEFAULT '{}';
