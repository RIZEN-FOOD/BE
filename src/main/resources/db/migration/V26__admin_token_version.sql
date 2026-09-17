-- 관리자 세션 번호.
--
-- 관리자 로그인 토큰에 이 번호를 함께 싣고, 요청마다 DB 의 번호와 대조한다.
-- 비밀번호 변경·초기화, 계정 중지, 권한 변경, 로그아웃 때 번호를 올리면
-- 그전에 발급된 토큰이 모두 무효가 된다 — 훔친 쿠키나 퇴사자 계정을 즉시 끊기 위해서다.
ALTER TABLE admin_user ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
