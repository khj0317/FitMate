-- 단체방 입장·퇴장 안내(SYSTEM): 보낸 사람 없이 content만 가진다
ALTER TABLE chat_messages DROP CONSTRAINT chk_chat_messages_payload;
ALTER TABLE chat_messages ADD CONSTRAINT chk_chat_messages_payload CHECK (
    (message_type = 'TEXT' AND content IS NOT NULL)
    OR (message_type = 'IMAGE' AND image_url IS NOT NULL)
    OR (message_type = 'SYSTEM' AND content IS NOT NULL AND sender_id IS NULL)
);
