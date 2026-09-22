-- ============================================================
--  V33. 할인코드 사용 기록
--
--  V9 에서 coupon 구조만 세워두고 화면·API 를 붙이지 않았다. 이제 붙인다.
--
--  ★ coupon_issue(회원에게 발급된 쿠폰 한 장)는 건드리지 않는다.
--    그 테이블은 member_id 가 필수라 비회원이 못 쓴다. 코드를 직접 입력하는
--    방식은 "발급"이 없으므로 주문에 코드를 달아 기록한다. 발급형 쿠폰을
--    나중에 만들 때 coupon_issue 를 그대로 쓰면 된다.
--
--  ★ 코드별 매출 집계는 orders 를 coupon_id 로 묶어서 낸다.
--    used_count 는 "남은 수량"을 원자적으로 깎기 위한 값이다. 집계용이 아니다.
--    (재고와 같은 방식: UPDATE ... WHERE used_count < total_quantity)
-- ============================================================

ALTER TABLE orders
    ADD COLUMN coupon_id BIGINT REFERENCES coupon (id) ON DELETE SET NULL;

COMMENT ON COLUMN orders.coupon_id IS '적용된 할인코드. 없으면 NULL. 코드가 지워져도 주문은 남는다';

-- 코드별 사용 건수·매출을 뽑을 때 쓴다.
CREATE INDEX idx_orders_coupon ON orders (coupon_id) WHERE coupon_id IS NOT NULL;

-- 1인 사용 횟수를 비회원까지 세려면 주문자 연락처로 찾아야 한다.
--
-- ★ orderer_phone_encrypted 로는 찾을 수 없다. AES-GCM 이라 매번 새 IV 를 쓰고,
--   같은 번호라도 저장된 값이 매번 다르다. 그래서 조회 전용 해시를 따로 둔다.
--   원문을 되돌릴 수 없게 키가 들어간 HMAC 이다 (PhoneHasher).
--   번호를 찾는 데 쓰이는 값이므로 절대 화면으로 내보내지 않는다.
ALTER TABLE orders
    ADD COLUMN orderer_phone_hash VARCHAR(64);

COMMENT ON COLUMN orders.orderer_phone_hash IS
    '주문자 번호의 조회용 HMAC. 같은 사람인지 맞춰보는 용도로만 쓴다. 외부로 내보내지 않는다';

CREATE INDEX idx_orders_coupon_phone ON orders (coupon_id, orderer_phone_hash)
    WHERE coupon_id IS NOT NULL;

ALTER TABLE coupon
    ADD COLUMN used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0);

COMMENT ON COLUMN coupon.used_count IS '지금까지 쓰인 횟수. total_quantity 와 비교해 남은 수량을 막는다';
