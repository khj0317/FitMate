-- 데모 사용자 닉네임을 "데모_01" 대신 실제 사람 이름처럼 바꾼다 (이후 V11에서 별명으로 다시 바꿈).
-- 성별에 맞는 이름을 고른다. 이미 만들어진 데모 데이터(배포 DB 포함)에만 적용되고,
-- 이름을 바꾼 적 있거나 같은 닉네임이 이미 있으면 건너뛴다. 알림·단체방 안내 메시지에 저장된 옛 이름도 바꾼다.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT u.id, u.nickname AS old_name,
               CASE WHEN u.gender = 'MALE' THEN n.male ELSE n.female END AS new_name
        FROM (VALUES
            ('demo01', '김도현', '김서윤'), ('demo02', '이준서', '이수아'), ('demo03', '박현우', '박지은'),
            ('demo04', '최민재', '최윤정'), ('demo05', '정우진', '김하린'), ('demo06', '박준호', '정다은'),
            ('demo07', '이성민', '이유나'), ('demo08', '강지훈', '정미경'), ('demo09', '조현석', '강서연'),
            ('demo10', '윤재석', '조은영'), ('demo11', '정민혁', '한소영'), ('demo12', '최태윤', '임채은'),
            ('demo13', '강동욱', '송지아'), ('demo14', '한승우', '윤소희'), ('demo15', '임현준', '한지혜'),
            ('demo16', '송민규', '임다은'), ('demo17', '권태호', '권나연'), ('demo18', '유진우', '유가은'),
            ('demo19', '오태윤', '송예린'), ('demo20', '홍준영', '오세영'), ('demo21', '배성훈', '문채원'),
            ('demo22', '서동욱', '서지현'), ('demo23', '신유찬', '신예진'), ('demo24', '조영호', '조수빈'),
            ('demo25', '문재민', '배수빈'), ('demo26', '백승현', '홍나영'), ('demo27', '노정훈', '권민지'),
            ('demo28', '전민규', '유선영'), ('demo29', '안재원', '안시은'), ('demo30', '장석진', '장유진')
        ) AS n (login_id, male, female)
        JOIN users u ON u.login_id = n.login_id AND u.nickname = '데모_' || substr(n.login_id, 5)
    LOOP
        UPDATE users SET nickname = r.new_name
        WHERE id = r.id AND NOT EXISTS (SELECT 1 FROM users WHERE nickname = r.new_name);

        IF FOUND THEN
            UPDATE notifications SET title = replace(title, r.old_name, r.new_name), body = replace(body, r.old_name, r.new_name)
            WHERE title LIKE '%' || r.old_name || '%' OR body LIKE '%' || r.old_name || '%';
            UPDATE chat_messages SET content = replace(content, r.old_name, r.new_name)
            WHERE message_type = 'SYSTEM' AND content LIKE '%' || r.old_name || '%';
        END IF;
    END LOOP;
END $$;
