-- 배송조회 주소.
--
-- 손님이 마이페이지에서 송장번호를 눌러 택배사 조회 화면으로 갈 수 있게 한다.
-- 택배사가 바뀔 수 있으므로(10월 변경 예정) 주소를 설정으로 둔다.
-- {{송장번호}} 자리에 실제 송장번호가 들어간다. 비우면 조회 버튼이 나오지 않는다.
INSERT INTO site_setting (key, value, description) VALUES
    ('shipping.tracking_url',
     'https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo={{송장번호}}',
     '배송조회 주소. {{송장번호}} 자리에 송장번호가 들어갑니다. 택배사가 바뀌면 여기를 고치세요. 비우면 조회 버튼이 나오지 않습니다.')
ON CONFLICT (key) DO NOTHING;
