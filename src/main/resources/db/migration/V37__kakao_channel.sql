-- 카카오톡 채널 주소 (2026-10-06)
--
-- 사이트 오른쪽 아래 문의 메뉴(퀵메뉴)에 «카카오톡 문의»로 걸린다. 비우면 메뉴에서 빠진다.
-- 대표가 채널을 열어 주소를 받았으므로 그 값으로 채워 둔다. 바뀌면 관리자 › 사이트 설정에서 고친다.
INSERT INTO site_setting (key, value, description) VALUES
    ('sns.kakao_channel', 'https://pf.kakao.com/_fxbxixiX',
     '카카오톡 채널 주소. 사이트 오른쪽 아래 문의 메뉴에 «카카오톡 문의»로 나옵니다.')
ON CONFLICT (key) DO NOTHING;
