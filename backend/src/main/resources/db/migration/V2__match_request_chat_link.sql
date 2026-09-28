-- 두 사람 사이의 1:1 채팅방은 하나만 존재하도록 "작은ID:큰ID" 키에 유니크 제약
ALTER TABLE chat_rooms ADD COLUMN direct_key VARCHAR(50);
ALTER TABLE chat_rooms ADD CONSTRAINT uq_chat_rooms_direct_key UNIQUE (direct_key);

-- 수락된 매칭 요청에서 바로 채팅방으로 이동할 수 있도록 연결
ALTER TABLE match_requests ADD COLUMN chat_room_id BIGINT REFERENCES chat_rooms(id) ON DELETE SET NULL;

CREATE INDEX idx_match_requests_requester ON match_requests (requester_id, status);
