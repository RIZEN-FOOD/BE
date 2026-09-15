-- ============================================================
--  V21. 상품정보 고시 항목 추가
--
--  스마트스토어 등과 같은 "상품정보" 표기를 관리자에서 채울 수 있게
--  기존 표시사항(product_label)에 없는 항목만 더한다.
--    - brand        브랜드
--    - origin       원산지 (예: 국산)
--    - grain_type   곡물유형 (예: 쌀(라이스))
--    - calorie_info 열량 표시 (예: 352kcal / 100g당) — 영양성분표와 별개의 요약 표기
--  제조사·보관방법·소비기한은 기존 컬럼, 중량은 product.weight_g 를 쓴다.
-- ============================================================

ALTER TABLE product_label
    ADD COLUMN IF NOT EXISTS brand        VARCHAR(120),
    ADD COLUMN IF NOT EXISTS origin       VARCHAR(120),
    ADD COLUMN IF NOT EXISTS grain_type   VARCHAR(120),
    ADD COLUMN IF NOT EXISTS calorie_info VARCHAR(120);
