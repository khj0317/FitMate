-- 채팅 사진 메시지: 텍스트 메시지는 content, 사진 메시지는 image_url을 가진다
ALTER TABLE chat_messages ADD COLUMN message_type VARCHAR(10) NOT NULL DEFAULT 'TEXT';
ALTER TABLE chat_messages ADD COLUMN image_url VARCHAR(500);
-- 사진 크기를 미리 알면 화면에서 자리를 먼저 잡아 두어 로딩 중 스크롤이 튀지 않는다
ALTER TABLE chat_messages ADD COLUMN image_width SMALLINT;
ALTER TABLE chat_messages ADD COLUMN image_height SMALLINT;
ALTER TABLE chat_messages ALTER COLUMN content DROP NOT NULL;

ALTER TABLE chat_messages ADD CONSTRAINT chk_chat_messages_payload CHECK (
    (message_type = 'TEXT' AND content IS NOT NULL)
    OR (message_type = 'IMAGE' AND image_url IS NOT NULL)
);
