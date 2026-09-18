-- 메인 FEATURES 모바일 전용 사진 (2026-09-18 승인).
--
-- FEATURES 섹션이 사진을 화면 전체 배경으로 깔면서, 가로 사진이 세로 화면에서 좌우로 잘린다.
-- 세로 사진을 따로 올릴 수 있게 한다. 비어 있으면 기존 사진(image_key)을 그대로 쓴다.

ALTER TABLE main_feature ADD COLUMN image_mobile_key VARCHAR(500);
