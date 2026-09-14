-- ============================================================
--  V20. 메인 히어로 배너 — 제품 히어로 필드 확장
--
--  히어로 배너를 관리자에서 완전 관리하기 위해 제품 히어로 필드를 넓힌다.
--    - 구성 이미지가 4종(기둥 + 우상단 + 우하단 + 좌하단)이 되도록 accent3 추가
--      (기존 accent1=우상단, accent2=좌하단, backdrop=기둥, 신규 accent3=우하단)
--    - 배너 전용 문구(메인/서브). 비우면 상품명·부제를 쓴다.
--    - 표시 순서(hero_sort), 노출 여부(hero_enabled) — 배너별 활성/비활성.
--
--  가격·재고·품절은 계속 상품(product)에서 관리한다.
-- ============================================================

ALTER TABLE product
    ADD COLUMN IF NOT EXISTS hero_accent3_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS hero_headline    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS hero_subcopy     VARCHAR(300),
    ADD COLUMN IF NOT EXISTS hero_sort        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS hero_enabled     BOOLEAN NOT NULL DEFAULT FALSE;

-- 현재 판매 제품(플레인)은 기본 노출. 신제품은 관리자에서 켠다.
UPDATE product SET hero_enabled = TRUE WHERE slug = 'cream-of-rice';

CREATE INDEX IF NOT EXISTS idx_product_hero
    ON product (hero_sort ASC, id ASC) WHERE hero_enabled;
