-- 출고 대행(3PL, 와이에스컴퍼니)·택배사·반품지와 도서산간 우편번호 설정.
--
-- 운영 DB 는 이 마이그레이션들로 새로 만들어진다. 대표가 알려준 사업자·배송 값이
-- 처음부터 들어가도록 채운다. 관리자 화면에서 이미 채운 값은 덮어쓰지 않는다(빈 칸만 채움).

INSERT INTO site_setting (key, value, description) VALUES
    ('shipping.carrier', '롯데택배',
     '택배사 이름. 배송·교환·환불 안내 페이지에 표시됩니다.'),
    ('shipping.return_address', '경기도 평택시 고덕면 도시지원1길 111 에스타워프라임 405호 (와이에스컴퍼니)',
     '반품·교환 상품을 보낼 주소(출고 대행사 창고). 배송·교환·환불 안내 페이지에 표시됩니다.'),
    ('shipping.island_zip_ranges', '',
     '도서산간 추가 배송비를 받을 우편번호. 비워 두면 기본 목록(제주·울릉·옹진·신안 등)을 씁니다.')
ON CONFLICT (key) DO NOTHING;

UPDATE site_setting AS s
SET value = v.value
FROM (VALUES
    ('company.ceo',             '정수교'),
    ('company.biz_no',          '891-04-03570'),
    ('company.mail_order_no',   '2025-충북충주-0947'),
    ('company.address',         '충청북도 충주시 주덕읍 조동길 42-2 (우 27459)'),
    ('company.privacy_officer', '정수교 (대표)'),
    ('company.tel',             '070-8098-9542'),
    ('company.email',           'rizenfoods@gmail.com'),
    ('company.hours',           '09:00 ~ 17:00'),
    ('order.cutoff_time',       '14:00')
) AS v(key, value)
WHERE s.key = v.key
  AND (s.value IS NULL OR s.value = '');

-- 배송비: 기본 3,500원 / 50,000원 이상 무료 / 도서산간 +3,000원 (2026-09-15 대표 확정).
-- 처음 심은 값(3,000원·도서산간 0원) 그대로일 때만 바꾼다 — 관리자가 고친 값은 건드리지 않는다.
UPDATE shipping_policy
SET base_fee = 3500, island_extra_fee = 3000
WHERE visible AND base_fee = 3000 AND island_extra_fee = 0;
