-- 바깥 판매 경로 · 반품/교환 배송비 · 반품지 우편번호 (2026-10-02 승인)
--
-- 네이버페이 주문형과 카카오 톡체크아웃 주문은 우리 장바구니·결제를 거치지 않고 바깥에서 들어온다.
-- 같은 주문 테이블에 받아 재고 차감·관리자 주문 목록·송장·취소·반품을 한 곳에서 처리한다.

-- 1) 주문이 어디서 들어왔는지. 지금까지의 주문은 전부 자사몰이다.
ALTER TABLE orders ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'MALL';
ALTER TABLE orders ADD CONSTRAINT chk_orders_channel
    CHECK (channel IN ('MALL', 'NAVERPAY', 'KAKAO_CHECKOUT'));

-- 2) 바깥 서비스의 주문번호(네이버페이 주문번호 등). 자사몰 주문은 비어 있다.
--    같은 경로의 같은 주문을 두 번 가져오지 않게 경로별로 하나만 허용한다(주문 동기화가 재시도돼도 안전).
ALTER TABLE orders ADD COLUMN external_order_no VARCHAR(64);
CREATE UNIQUE INDEX ux_orders_channel_external_order_no
    ON orders (channel, external_order_no) WHERE external_order_no IS NOT NULL;

-- 3) 주문 상품 한 줄마다의 바깥 번호(네이버페이 상품주문번호 등). 발송·취소·반품은 이 번호로 주고받는다.
ALTER TABLE order_item ADD COLUMN external_product_order_id VARCHAR(64);
CREATE INDEX ix_order_item_external_product_order_id
    ON order_item (external_product_order_id) WHERE external_product_order_id IS NOT NULL;

-- 4) 단순 변심 반품(편도)·교환(왕복) 배송비. 비어 있으면 아직 정하지 않은 것이다.
--    네이버페이 주문형은 상품정보에 이 금액을 숫자로 요구한다.
ALTER TABLE shipping_policy ADD COLUMN return_fee INT;
ALTER TABLE shipping_policy ADD COLUMN exchange_fee INT;
ALTER TABLE shipping_policy ADD CONSTRAINT chk_shipping_policy_return_fee CHECK (return_fee IS NULL OR return_fee >= 0);
ALTER TABLE shipping_policy ADD CONSTRAINT chk_shipping_policy_exchange_fee CHECK (exchange_fee IS NULL OR exchange_fee >= 0);

-- 5) 반품지 우편번호. 주소는 shipping.return_address 에 있다. 바깥 서비스는 우편번호를 따로 받는다.
INSERT INTO site_setting (key, value, description) VALUES
    ('shipping.return_zipcode', '',
     '반품지 우편번호. 숫자 5자리. 네이버페이·톡체크아웃에 반품지로 전달됩니다.')
ON CONFLICT (key) DO NOTHING;
