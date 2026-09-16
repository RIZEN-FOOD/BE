-- 메인 FEATURES 4칸(큰 문구·작은 문구·사진)을 관리자에서 고칠 수 있게 한다.
--
-- 지금 화면에 나가는 문구를 그대로 심는다 (2026-09-14 승인 카피).
-- 값을 비우면 코드의 기본 문구·기본 사진이 그대로 나간다.
-- 3번 작은 문구만 비워 둔다 — 비어 있으면 영양성분(DB)의 수치로 문장을 자동으로 만든다.

INSERT INTO site_setting (key, value, description) VALUES
    ('main.feature1.title', '국산 멥쌀 한 가지',                                              'FEATURES 1번 — 큰 문구'),
    ('main.feature1.desc',  '국산 멥쌀만으로 만들었습니다. 원재료명에 멥쌀 한 줄뿐입니다.',      'FEATURES 1번 — 작은 문구'),
    ('main.feature1.image', '',                                                               'FEATURES 1번 — 사진'),
    ('main.feature2.title', '자극이 적은 담백한 맛',                                          'FEATURES 2번 — 큰 문구'),
    ('main.feature2.desc',  '고운 입자로 갈아, 조리하면 죽처럼 부드럽고 담백한 맛이 납니다.',   'FEATURES 2번 — 작은 문구'),
    ('main.feature2.image', '',                                                               'FEATURES 2번 — 사진'),
    ('main.feature3.title', '운동 전후 탄수화물 보충',                                        'FEATURES 3번 — 큰 문구'),
    ('main.feature3.desc',  '',                                                               'FEATURES 3번 — 작은 문구 (비우면 영양성분 수치로 자동 작성)'),
    ('main.feature3.image', '',                                                               'FEATURES 3번 — 사진'),
    ('main.feature4.title', '다양한 맞춤 레시피',                                             'FEATURES 4번 — 큰 문구'),
    ('main.feature4.desc',  '프로틴 파우더, 견과류, 과일 등을 조합해 기호에 맞춰 손쉽게 완성할 수 있습니다.', 'FEATURES 4번 — 작은 문구'),
    ('main.feature4.image', '',                                                               'FEATURES 4번 — 사진')
ON CONFLICT (key) DO NOTHING;
