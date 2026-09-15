-- 히어로 캐러셀용 제품 필드.
--   hero_color     : 슬라이드 배경색 (#RRGGBB). 비우면 프론트가 브랜드 기본색을 쓴다.
--   hero_image_key : 배경 위에 띄울 누끼(투명배경) 이미지 키. 비우면 대표 이미지로 폴백.
-- 색·이미지를 코드에 박지 않고 제품에서 읽어, 신제품이 늘어도 관리자만으로 히어로가 채워진다.
ALTER TABLE product ADD COLUMN hero_color     VARCHAR(9);
ALTER TABLE product ADD COLUMN hero_image_key VARCHAR(500);
