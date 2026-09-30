-- 처음엔 낙관적 락용으로 만들었지만, 정원은 조건부 UPDATE 한 문장으로 지키게 되어 쓰지 않는 컬럼
ALTER TABLE gatherings DROP COLUMN version;
