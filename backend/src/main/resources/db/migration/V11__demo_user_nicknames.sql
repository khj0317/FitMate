-- 데모 사용자 닉네임을 "달리는판다"처럼 서비스에서 흔히 쓰는 별명으로 바꾼다 (DemoDataInitializer.NICKNAMES와 같은 값).
-- 처음 이름("데모_01")이나 V10에서 바꾼 실제 이름 그대로인 데모 사용자만 바꾸고, 같은 닉네임이 이미 있으면 건너뛴다.
-- 알림·단체방 안내 메시지에 저장된 옛 이름도 바꾼다.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT u.id, u.nickname AS old_name, n.nickname AS new_name
        FROM (VALUES
            ('demo01', '달리는판다', '김도현', '김서윤'), ('demo02', '초록고래', '이준서', '이수아'),
            ('demo03', '노란병아리', '박현우', '박지은'), ('demo04', '하늘다람쥐', '최민재', '최윤정'),
            ('demo05', '주황여우', '정우진', '김하린'), ('demo06', '느린거북이', '박준호', '정다은'),
            ('demo07', '수영하는오리', '이성민', '이유나'), ('demo08', '사막여우', '강지훈', '정미경'),
            ('demo09', '흰토끼', '조현석', '강서연'), ('demo10', '별빛사슴', '윤재석', '조은영'),
            ('demo11', '꿀벌', '정민혁', '한소영'), ('demo12', '청설모', '최태윤', '임채은'),
            ('demo13', '푸른물개', '강동욱', '송지아'), ('demo14', '아기호랑이', '한승우', '윤소희'),
            ('demo15', '라쿤', '임현준', '한지혜'), ('demo16', '부엉이', '송민규', '임다은'),
            ('demo17', '캥거루', '권태호', '권나연'), ('demo18', '고슴도치', '유진우', '유가은'),
            ('demo19', '노을고래', '오태윤', '송예린'), ('demo20', '코알라', '홍준영', '오세영'),
            ('demo21', '분홍돌고래', '배성훈', '문채원'), ('demo22', '펭귄', '서동욱', '서지현'),
            ('demo23', '햄스터', '신유찬', '신예진'), ('demo24', '수달', '조영호', '조수빈'),
            ('demo25', '산책하는곰', '문재민', '배수빈'), ('demo26', '치타', '백승현', '홍나영'),
            ('demo27', '미어캣', '노정훈', '권민지'), ('demo28', '오렌지고양이', '전민규', '유선영'),
            ('demo29', '구름양', '안재원', '안시은'), ('demo30', '무지개물고기', '장석진', '장유진')
        ) AS n (login_id, nickname, v10_male, v10_female)
        JOIN users u ON u.login_id = n.login_id
            AND u.nickname IN ('데모_' || substr(n.login_id, 5), n.v10_male, n.v10_female)
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
