-- 후기·공지 맨 위 배너 사진을 관리자가 바꿀 수 있게 키를 만든다 (V25 와 같은 방식).
--
-- 화면 코드에 파일 경로가 박혀 있으면 사진 한 장 바꾸는 데 개발자가 필요하다.
-- 비워 두면 번들 사진이 그대로 나가므로, 값을 채우기 전까지 화면은 달라지지 않는다.

INSERT INTO site_setting (key, value, description) VALUES
    ('main.page_hero_reviews', '', '후기 페이지 맨 위 배너 사진 (가로로 넓은 사진, 권장 1920x800 이상)'),
    ('main.page_hero_notice',  '', '공지사항 페이지 맨 위 배너 사진 (가로로 넓은 사진, 권장 1920x800 이상)')
ON CONFLICT (key) DO NOTHING;
