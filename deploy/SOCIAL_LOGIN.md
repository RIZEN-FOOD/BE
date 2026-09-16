# 간편 로그인(카카오·네이버) 연결하기

코드는 다 되어 있습니다. **키를 `.env`에 넣고 서버를 다시 켜면** 로그인 화면에 버튼이 생깁니다.
키가 비어 있는 쪽은 버튼이 아예 안 보입니다(누르면 실패하는 버튼을 두지 않습니다).

```
OAUTH_REDIRECT_BASE=https://www.rizenfood.co.kr   # 손님이 보는 주소, 끝에 / 없이 (로컬: http://localhost:3200)
KAKAO_CLIENT_ID=        # 카카오 REST API 키
KAKAO_CLIENT_SECRET=    # 카카오 Client Secret (켜는 것을 권장)
NAVER_CLIENT_ID=        # 네이버 Client ID
NAVER_CLIENT_SECRET=    # 네이버 Client Secret (필수)
```

## 동작 요약

1. 손님이 "카카오로 시작하기"를 누르면 서버가 카카오 로그인 화면으로 보냅니다.
2. 카카오가 `…/api/auth/oauth/kakao/callback`으로 돌려보내면 서버가 회원 정보를 읽습니다.
3. 결과에 따라
   - **이미 가입한 사람** → 바로 로그인
   - **처음 온 사람** → 약관·만 14세 동의 화면(`/auth/social-signup`) → 동의하면 가입·로그인
   - **같은 이메일로 이미 가입한 계정이 있음** → 합치지 않고 "원래 방법으로 로그인하세요" 안내
     (남의 이메일로 만든 소셜 계정이 기존 계정을 가로채지 못하게 하려는 결정입니다)

## Redirect URI (콘솔에 그대로 등록)

| 용도 | 카카오 | 네이버 |
|---|---|---|
| 운영 | `https://www.<도메인>/api/auth/oauth/kakao/callback` | `https://www.<도메인>/api/auth/oauth/naver/callback` |
| 로컬 | `http://localhost:3200/api/auth/oauth/kakao/callback` | `http://localhost:3200/api/auth/oauth/naver/callback` |

`OAUTH_REDIRECT_BASE`와 글자 하나까지 같아야 합니다. `www` 유무, `http`/`https`가 다르면
카카오·네이버가 "Redirect URI 불일치"로 거절합니다.

---

## 1. 카카오 (developers.kakao.com)

1. **내 애플리케이션 → 애플리케이션 추가하기**
   - 앱 이름: 라이즌푸드 / 회사명: 라이즌푸드
2. **앱 설정 → 앱 키** → `REST API 키`를 복사해 `KAKAO_CLIENT_ID`에 넣습니다.
3. **앱 설정 → 플랫폼 → Web 플랫폼 등록**
   - `https://www.<도메인>`, `http://localhost:3200`
4. **제품 설정 → 카카오 로그인**
   - 활성화 설정: **ON**
   - Redirect URI: 위 표의 카카오 주소 두 개
5. **제품 설정 → 카카오 로그인 → 동의항목**
   - 닉네임: 필수 동의
   - 카카오계정(이메일): **선택 동의**로 시작합니다.
     필수 동의로 받으려면 **비즈 앱 전환**(사업자등록번호 인증)이 필요합니다.
     이메일을 못 받아도 가입은 되고, 주문할 때 이메일을 따로 받습니다.
6. **제품 설정 → 카카오 로그인 → 보안** → Client Secret **코드 생성 → 활성화 상태: 사용함**
   → 값을 `KAKAO_CLIENT_SECRET`에 넣습니다.
7. (권장) **앱 설정 → 비즈니스 → 비즈 앱 전환** — 사업자등록번호로 전환하면 이메일 필수 동의가 가능합니다.
8. 개발자를 함께 쓰려면 **앱 설정 → 팀 관리 → 팀원 초대**.

## 2. 네이버 (developers.naver.com)

1. **Application → 애플리케이션 등록**
   - 애플리케이션 이름: 라이즌푸드 (로그인 화면에 손님에게 보입니다)
   - 사용 API: **네이버 로그인**
   - 제공 정보 선택: **이메일 주소(필수)**, **이름 또는 별명(필수)**
2. **로그인 오픈 API 서비스 환경 → PC 웹** (모바일 웹도 같은 주소로 추가)
   - 서비스 URL: `https://www.<도메인>` (로컬 테스트 때는 `http://localhost:3200`)
   - 네이버 로그인 Callback URL: 위 표의 네이버 주소 두 개
3. 등록 후 **Client ID / Client Secret**을 `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`에 넣습니다.
4. ⚠️ **개발 중 상태**에서는 **멤버관리에 등록한 네이버 아이디만** 로그인할 수 있습니다.
   테스트할 아이디를 **멤버관리 → 테스터 ID**에 추가하세요.
5. 오픈 전에 **검수 요청**을 해야 모든 손님이 쓸 수 있습니다.
   - 서비스 화면 캡처(로그인 버튼·동의 화면), 개인정보처리방침 주소(`/policy/privacy`)를 준비합니다.
   - 검수는 보통 며칠 걸립니다. 서버·도메인이 열린 뒤 신청합니다.

---

## 확인 방법

1. `.env`에 키를 넣고 서버를 다시 켭니다 (`./gradlew bootRun`은 `.env`를 직접 읽습니다).
2. `GET /api/auth/oauth/providers` → `{"kakao":true,"naver":true}`처럼 켜진 쪽이 `true`.
3. 로그인 화면에 버튼이 보이면 눌러서 실제로 가입·로그인해 봅니다.

## 안 될 때

| 증상 | 원인 |
|---|---|
| 버튼이 안 보임 | 키가 비었거나 서버를 다시 안 켬. 네이버는 Secret까지 있어야 켜짐 |
| 카카오 "KOE006" / 네이버 "redirect_uri 불일치" | 콘솔의 Redirect URI와 `OAUTH_REDIRECT_BASE`가 다름 |
| 로그인 화면에 "로그인 시간이 지났습니다" | 로그인 화면에서 10분 넘게 머물렀거나, 다른 탭에서 다시 시작함 |
| 네이버에서 "권한이 없습니다" | 개발 중 상태 — 멤버관리에 테스터 ID 추가 또는 검수 요청 |
| 카카오 이메일이 안 옴 | 이메일이 선택 동의라 손님이 거부했거나, 비즈 앱 전환 전 |
