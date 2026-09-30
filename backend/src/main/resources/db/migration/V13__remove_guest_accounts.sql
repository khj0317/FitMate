-- 체험 계정 기능을 되돌리면서 그동안 만들어진 체험 계정(role = 'GUEST')을 지운다.
-- (코드의 Role에 GUEST가 없으면 이 사용자를 읽을 때 오류가 나기 때문)
-- 연결된 데이터는 회원 탈퇴와 같이 외래 키(ON DELETE CASCADE / SET NULL)로 정리된다.
DELETE FROM users WHERE role = 'GUEST';
