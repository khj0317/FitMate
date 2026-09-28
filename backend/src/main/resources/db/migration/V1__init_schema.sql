-- Supabase에서는 대시보드에서 postgis를 먼저 활성화해도 되고, 이미 있으면 건너뜀
CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================
-- 회원 / 프로필
-- ============================================================
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    nickname            VARCHAR(30)  NOT NULL UNIQUE,
    bio                 VARCHAR(500),
    profile_image_url   VARCHAR(500),
    gender              VARCHAR(10),
    birth_year          SMALLINT,
    activity_location   GEOGRAPHY(POINT, 4326),
    activity_area_name  VARCHAR(100),
    search_radius_km    SMALLINT     NOT NULL DEFAULT 5,
    manner_score        NUMERIC(4,1) NOT NULL DEFAULT 36.5,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_activity_location ON users USING GIST (activity_location);

CREATE TABLE sports (
    id    SMALLSERIAL PRIMARY KEY,
    code  VARCHAR(30) NOT NULL UNIQUE,
    name  VARCHAR(30) NOT NULL
);

CREATE TABLE user_sports (
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sport_id     SMALLINT    NOT NULL REFERENCES sports(id),
    skill_level  VARCHAR(20) NOT NULL, -- BEGINNER / INTERMEDIATE / ADVANCED
    PRIMARY KEY (user_id, sport_id)
);
CREATE INDEX idx_user_sports_sport ON user_sports (sport_id);

-- 운동 가능한 요일/시간대 (매칭 점수 계산용)
CREATE TABLE user_available_times (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week  SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1=월 ~ 7=일
    start_time   TIME     NOT NULL,
    end_time     TIME     NOT NULL,
    CHECK (start_time < end_time)
);
CREATE INDEX idx_user_available_times_user ON user_available_times (user_id);

-- ============================================================
-- 1:1 매칭
-- ============================================================
CREATE TABLE match_requests (
    id            BIGSERIAL PRIMARY KEY,
    requester_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sport_id      SMALLINT    NOT NULL REFERENCES sports(id),
    message       VARCHAR(300),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / ACCEPTED / REJECTED / CANCELED
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at  TIMESTAMPTZ,
    CHECK (requester_id <> receiver_id)
);
-- 같은 상대에게 대기 중인 요청은 하나만 (동시 요청 중복 방지를 DB 레벨에서도 보장)
CREATE UNIQUE INDEX uq_match_requests_pending
    ON match_requests (requester_id, receiver_id) WHERE status = 'PENDING';
CREATE INDEX idx_match_requests_receiver ON match_requests (receiver_id, status);

-- ============================================================
-- 모임 (정원 제한)
-- ============================================================
CREATE TABLE gatherings (
    id             BIGSERIAL PRIMARY KEY,
    host_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sport_id       SMALLINT     NOT NULL REFERENCES sports(id),
    title          VARCHAR(100) NOT NULL,
    description    TEXT,
    location       GEOGRAPHY(POINT, 4326) NOT NULL,
    place_name     VARCHAR(100) NOT NULL,
    starts_at      TIMESTAMPTZ  NOT NULL,
    capacity       SMALLINT     NOT NULL CHECK (capacity BETWEEN 2 AND 50),
    current_count  SMALLINT     NOT NULL DEFAULT 1,
    status         VARCHAR(20)  NOT NULL DEFAULT 'RECRUITING', -- RECRUITING / CLOSED / COMPLETED / CANCELED
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (current_count <= capacity)
);
CREATE INDEX idx_gatherings_location ON gatherings USING GIST (location);
CREATE INDEX idx_gatherings_status_starts ON gatherings (status, starts_at);

CREATE TABLE gathering_participants (
    gathering_id  BIGINT      NOT NULL REFERENCES gatherings(id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL DEFAULT 'JOINED', -- JOINED / CANCELED / ATTENDED / NO_SHOW
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (gathering_id, user_id)
);
CREATE INDEX idx_gathering_participants_user ON gathering_participants (user_id);

-- ============================================================
-- 채팅
-- ============================================================
CREATE TABLE chat_rooms (
    id            BIGSERIAL PRIMARY KEY,
    type          VARCHAR(20) NOT NULL, -- DIRECT / GATHERING
    gathering_id  BIGINT UNIQUE REFERENCES gatherings(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_messages (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT        NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id   BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    content     VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- 커서 기반 페이지네이션 (room_id, id < cursor ORDER BY id DESC)
CREATE INDEX idx_chat_messages_room_id ON chat_messages (room_id, id DESC);

CREATE TABLE chat_room_members (
    room_id               BIGINT      NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id               BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_message_id  BIGINT,
    joined_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, user_id)
);
CREATE INDEX idx_chat_room_members_user ON chat_room_members (user_id);

-- ============================================================
-- 기본 데이터
-- ============================================================
INSERT INTO sports (code, name) VALUES
    ('GYM', '헬스'),
    ('RUNNING', '러닝'),
    ('CLIMBING', '클라이밍'),
    ('SWIMMING', '수영'),
    ('TENNIS', '테니스'),
    ('BADMINTON', '배드민턴'),
    ('CYCLING', '자전거'),
    ('YOGA', '요가'),
    ('FUTSAL', '풋살'),
    ('HIKING', '등산');
