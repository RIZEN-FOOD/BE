-- 히어로: 제품 뒤에 세로로 겹치는 배경 이미지(스플래시 등).
-- 투명 PNG 키. 비우면 배경 이미지 없이 제품만.
ALTER TABLE product ADD COLUMN hero_backdrop_key VARCHAR(500);
