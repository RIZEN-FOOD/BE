-- 주문이 장바구니에서 왔는지(true) 상품 페이지 «바로 구매»에서 왔는지(false).
--
-- 결제가 확정되면 주문에 담긴 상품을 장바구니에서 빼는데, 그 매칭이 «상품+옵션» 이다.
-- 이 표시가 없으면 바로구매로 1개를 사도 장바구니에 담아둔 같은 상품이 함께 사라진다.
-- 기존 주문은 전부 장바구니에서 왔으므로 기본값 true 로 채운다.

ALTER TABLE orders
    ADD COLUMN from_cart BOOLEAN NOT NULL DEFAULT true;
