-- 히어로 슬라이드 장식(떠다니는 재료 이미지) 슬롯 2개.
--   제품 누끼 옆에 그 맛의 재료(쌀·브라우니·피넛버터 등)를 은은히 띄운다.
--   투명 PNG 키를 저장한다. 비우면 장식 없이 제품만 뜬다.
ALTER TABLE product ADD COLUMN hero_accent1_key VARCHAR(500);
ALTER TABLE product ADD COLUMN hero_accent2_key VARCHAR(500);
