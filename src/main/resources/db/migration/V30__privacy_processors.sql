-- 개인정보 처리 수탁업체.
--
-- 주문 정보를 외부에 넘기면(출고 대행·택배·결제) 개인정보 처리 위탁이고,
-- 개인정보보호법 제26조에 따라 수탁업체와 업무 내용을 처리방침에 공개해야 한다.
-- 업체가 바뀔 때마다 배포하지 않도록 설정으로 뺀다. 비어 있으면 화면에 '확인 후 표기'로 나온다.
INSERT INTO site_setting (key, value, description) VALUES
    ('privacy.processor_fulfillment', '와이에스컴퍼니',
     '상품 보관·출고를 맡은 업체 이름. 개인정보처리방침의 위탁 표에 나옵니다.'),
    ('privacy.processor_delivery',    '롯데택배',
     '상품 배송을 맡은 택배사 이름. 개인정보처리방침의 위탁 표에 나옵니다.'),
    ('privacy.processor_payment',     '',
     '결제 처리(PG)를 맡은 업체 이름. 계약이 끝나면 적어 주세요.')
ON CONFLICT (key) DO NOTHING;
