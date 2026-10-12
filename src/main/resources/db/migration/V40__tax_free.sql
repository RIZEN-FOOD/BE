-- 과세·면세 구분 (2026-10-12)
--
-- 플레인처럼 면세인 상품이 있어 부가세 신고 때 과세·면세 매출을 나눠야 한다.
--
-- product.tax_free     관리자가 상품마다 고른다. 기본은 과세(false).
-- order_item.tax_free  주문할 때의 과세 구분을 스냅샷으로 남긴다 (상품명·가격과 같은 원칙).
--                      ★ 이 칸이 생기기 전 주문은 NULL 이다 — 그때는 구분을 기록하지 않았으므로
--                        매출 집계가 그 상품의 지금 설정을 따른다. 새 주문부터는 항상 true/false 가 들어간다.
ALTER TABLE product ADD COLUMN tax_free BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE order_item ADD COLUMN tax_free BOOLEAN;

COMMENT ON COLUMN product.tax_free IS '면세 상품이면 true (기본 과세)';
COMMENT ON COLUMN order_item.tax_free IS '주문 당시 면세 여부 스냅샷. NULL = 구분 도입 전 주문(상품의 현재 설정을 따른다)';
