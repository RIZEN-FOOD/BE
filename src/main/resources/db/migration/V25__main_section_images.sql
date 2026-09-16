-- 메인 화면 사진을 관리자가 올릴 수 있게 키를 만든다.
--
-- 지금까지는 화면 코드에 파일 경로가 박혀 있어 사진을 바꾸려면 개발자가 필요했다.
-- 비워 두면 기존 번들 사진이 그대로 나가므로, 값을 채우기 전까지 화면은 달라지지 않는다.

INSERT INTO site_setting (key, value, description) VALUES
    ('main.nutrition_image', '', '영양성분 띠 배경 사진 (가로로 넓은 사진, 권장 1920x1080 이상)'),
    ('main.footer_image',    '', '화면 하단 배너의 제품 사진 (배경이 없는 누끼 사진 권장)')
ON CONFLICT (key) DO NOTHING;
