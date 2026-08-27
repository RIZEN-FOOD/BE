-- ============================================================
--  V10. 초기 운영 데이터
--  상품 데이터는 여기 넣지 않는다. 관리자 화면에서 등록한다.
-- ============================================================

-- 배송비 정책: 기본 3,000원 / 50,000원 이상 무료
INSERT INTO shipping_policy (name, base_fee, free_threshold, island_extra_fee, visible)
VALUES ('기본 배송비', 3000, 50000, 0, TRUE);


-- 사이트 설정. 값은 대표가 관리자 화면에서 채운다.
-- description 은 관리자 화면에 그대로 안내 문구로 보여준다.
INSERT INTO site_setting (key, value, description) VALUES
    ('company.name',            '라이즌푸드', '사업자명'),
    ('company.ceo',             '',          '대표자 성명'),
    ('company.address',         '',          '사업장 주소'),
    ('company.biz_no',          '',          '사업자등록번호'),
    ('company.mail_order_no',   '',          '통신판매업 신고번호 (전자상거래법상 게시 의무)'),
    ('company.privacy_officer', '',          '개인정보 보호책임자'),
    ('company.tel',             '',          '고객센터 전화번호'),
    ('company.email',           '',          '고객센터 이메일'),
    ('company.hours',           '',          '고객센터 운영시간'),

    ('sns.instagram',           '',          '인스타그램 주소'),
    ('sns.youtube',             '',          '유튜브 주소'),
    ('sns.blog',                '',          '블로그 주소'),

    -- 배너에 쓰신 "오늘 주문, 오늘 출발" 문구의 근거가 되는 값이다.
    -- 실제로 지킬 수 있는 시각을 넣어야 한다. 못 지키면 그 자체가 허위광고다.
    ('order.cutoff_time',       '14:00',     '당일 출고 마감 시각 (HH:mm)'),
    ('order.guest_enabled',     'true',      '비회원 주문 허용 여부'),

    ('main.section.review',     'true',      '메인 후기 섹션 노출'),
    ('main.section.notice',     'true',      '메인 공지 섹션 노출');
