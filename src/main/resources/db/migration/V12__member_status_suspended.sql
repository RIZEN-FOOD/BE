-- 회원 상태에 'SUSPENDED'(관리자 정지)를 추가한다.
--
-- 기존 제약: status IN ('ACTIVE', 'DORMANT', 'WITHDRAWN')
--   - ACTIVE    정상
--   - DORMANT   휴면(장기 미접속) — 자동 전환 대상, 관리자 조치와 별개
--   - WITHDRAWN 탈퇴
-- 관리자가 약관 위반 등으로 로그인을 막는 '정지'는 휴면과 의미가 달라
-- 별도 상태(SUSPENDED)로 둔다.
--
-- 인라인 컬럼 CHECK 는 PostgreSQL 이 <table>_<column>_check 로 이름을 붙인다.
-- 이름이 다를 수 있으니 IF EXISTS 로 지우고 명시적 이름으로 다시 만든다.
ALTER TABLE member DROP CONSTRAINT IF EXISTS member_status_check;

ALTER TABLE member ADD CONSTRAINT member_status_check
    CHECK (status IN ('ACTIVE', 'DORMANT', 'SUSPENDED', 'WITHDRAWN'));
