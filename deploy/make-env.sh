#!/usr/bin/env bash
# 운영 서버 .env 만들기 — 서버에서 처음 한 번만 실행한다.
#
#   cd /opt/rizen && bash make-env.sh
#
# 무작위여야 하는 값(DB 비밀번호·토큰 서명키·휴대폰 암호화 키·첫 관리자 비밀번호·백업 암호)은
# 이 스크립트가 직접 만든다. 사람이 넣을 것은 도메인과 바깥 서비스 키뿐이다.
# 키를 입력할 때 화면에 표시되지 않는다. 모르는 값은 Enter 로 건너뛰고 나중에 .env 를 고치면 된다.
#
# ★ 이미 .env 가 있으면 멈춘다. 휴대폰 암호화 키가 바뀌면 저장된 연락처를 영영 못 읽는다.
# ★ 바깥 서비스 키는 작은따옴표로 감싸 저장한다(특수문자 보호). docker compose 와 backup.sh(bash)는
#   따옴표를 벗겨 읽는다. 'docker run --env-file' 은 따옴표까지 값으로 넣으니 쓰지 않는다.
set -euo pipefail

OUT="${1:-.env}"
if [ -e "$OUT" ]; then
  echo "이미 $OUT 가 있습니다. 덮어쓰지 않습니다. (새로 만들려면 먼저 백업 후 옮기세요)"
  exit 1
fi
command -v openssl >/dev/null || { echo "openssl 이 필요합니다: sudo apt install -y openssl"; exit 1; }

ask() {
  local prompt="$1" def="${2:-}" v
  read -r -p "$prompt${def:+ [$def]}: " v
  printf '%s' "${v:-$def}"
}

ask_secret() {
  local prompt="$1" v
  read -r -s -p "$prompt (없으면 Enter): " v
  echo >&2
  if [[ "$v" == *"'"* ]]; then
    echo "작은따옴표(')가 들어간 값은 받을 수 없습니다. 콘솔에서 값을 다시 확인해 주세요." >&2
    exit 1
  fi
  printf '%s' "$v"
}

# 영문+숫자가 반드시 섞인 무작위 문자열 (비밀번호 규칙을 항상 통과한다)
random_word() {
  local body
  body=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "$1")
  printf 'Rz%s7' "$body"
}

echo "== 라이즌푸드 운영 .env 만들기 =="
DOMAIN=$(ask "도메인 (www 없이, 예: rizenfood.co.kr)")
if ! [[ "$DOMAIN" =~ ^[a-z0-9-]+(\.[a-z0-9-]+)+$ ]]; then
  echo "도메인 형식이 이상합니다: '$DOMAIN' (소문자, www 없이)"
  exit 1
fi
ACME_EMAIL=$(ask "인증서 만료 안내를 받을 메일" "rizenfoods@gmail.com")
ADMIN_USER=$(ask "첫 관리자 아이디 (admin 보다 짐작하기 어려운 것 권장)" "rizen-owner")

echo
echo "-- 간편 로그인 키 (로컬 ryzen/.env 에 넣은 값. 카카오 시크릿은 재발급한 새 값 권장)"
KAKAO_CLIENT_ID=$(ask_secret "KAKAO_CLIENT_ID")
KAKAO_CLIENT_SECRET=$(ask_secret "KAKAO_CLIENT_SECRET")
NAVER_CLIENT_ID=$(ask_secret "NAVER_CLIENT_ID")
NAVER_CLIENT_SECRET=$(ask_secret "NAVER_CLIENT_SECRET")

echo
echo "-- 포트원 (계약 전이면 전부 Enter — 사이트는 뜨고 결제만 '준비 중'으로 막힙니다)"
PORTONE_API_SECRET=$(ask_secret "PORTONE_API_SECRET")
PORTONE_WEBHOOK_SECRET=$(ask_secret "PORTONE_WEBHOOK_SECRET")
PORTONE_STORE_ID=$(ask_secret "PORTONE_STORE_ID")
PORTONE_CHANNEL_KEY=$(ask_secret "PORTONE_CHANNEL_KEY (카드)")

echo
echo "-- 백업 버킷 (나중에 설정해도 됩니다 — 비어 있으면 백업이 돌지 않습니다)"
BACKUP_BUCKET=$(ask "BACKUP_BUCKET (Lightsail 버킷 이름)" "")
BACKUP_REGION=$(ask "BACKUP_REGION" "ap-northeast-2")
BACKUP_ACCESS_KEY_ID=$(ask_secret "BACKUP_ACCESS_KEY_ID")
BACKUP_SECRET_ACCESS_KEY=$(ask_secret "BACKUP_SECRET_ACCESS_KEY")

# ── 자동 생성 ──
DB_PASSWORD=$(openssl rand -base64 36 | tr -dc 'A-Za-z0-9' | head -c 32)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
PHONE_ENC_KEY=$(openssl rand -base64 32 | tr -d '\n')
ADMIN_PASSWORD=$(random_word 14)
BACKUP_PASSPHRASE=$(openssl rand -base64 36 | tr -dc 'A-Za-z0-9' | head -c 40)

umask 077
cat > "$OUT" <<EOF
# 운영 .env — make-env.sh 가 $(date '+%Y-%m-%d %H:%M') 에 만들었다. 절대 커밋하지 않는다.

# ── 도메인·HTTPS ──
DOMAIN=$DOMAIN
ACME_EMAIL=$ACME_EMAIL

# ── 이미지 태그 ──
API_TAG=latest
WEB_TAG=latest

# ── DB ──
DB_USERNAME=rizen
DB_PASSWORD=$DB_PASSWORD
DB_POOL_SIZE=5

# ── 인증·암호화 (자동 생성 — 바꾸면 로그인이 풀리고, PHONE_ENC_KEY 는 바꾸면 연락처를 못 읽는다) ──
JWT_SECRET=$JWT_SECRET
PHONE_ENC_KEY=$PHONE_ENC_KEY

# 관리자 계정이 하나도 없을 때만 첫 계정을 만든다. 첫 로그인 확인 후 두 줄을 지우고 재시작한다.
ADMIN_BOOTSTRAP_USERNAME=$ADMIN_USER
ADMIN_BOOTSTRAP_PASSWORD=$ADMIN_PASSWORD

# ── 주소 ──
CORS_ALLOWED_ORIGINS=https://www.$DOMAIN
OAUTH_REDIRECT_BASE=https://www.$DOMAIN
CLIENT_IP_HEADER=X-Real-IP

# ── 업로드 사진 ──
STORAGE_TYPE=local
STORAGE_LOCAL_URL=/uploads

# ── 백업 ──
BACKUP_PASSPHRASE=$BACKUP_PASSPHRASE
BACKUP_BUCKET=$BACKUP_BUCKET
BACKUP_REGION=$BACKUP_REGION
BACKUP_ACCESS_KEY_ID='$BACKUP_ACCESS_KEY_ID'
BACKUP_SECRET_ACCESS_KEY='$BACKUP_SECRET_ACCESS_KEY'

# ── 결제 (포트원) ──
PAYMENT_PROVIDER=portone
PORTONE_API_SECRET='$PORTONE_API_SECRET'
PORTONE_WEBHOOK_SECRET='$PORTONE_WEBHOOK_SECRET'
PORTONE_STORE_ID='$PORTONE_STORE_ID'
PORTONE_CHANNEL_KEY='$PORTONE_CHANNEL_KEY'
PORTONE_CHANNEL_KEY_TRANSFER=
PORTONE_CHANNEL_KEY_KAKAOPAY=
PORTONE_CHANNEL_KEY_NAVERPAY=
PORTONE_CHANNEL_KEY_TOSSPAY=

# ── 간편 로그인 ──
KAKAO_CLIENT_ID='$KAKAO_CLIENT_ID'
KAKAO_CLIENT_SECRET='$KAKAO_CLIENT_SECRET'
NAVER_CLIENT_ID='$NAVER_CLIENT_ID'
NAVER_CLIENT_SECRET='$NAVER_CLIENT_SECRET'
EOF
chmod 600 "$OUT"

cat <<EOF

== 완료: $OUT (본인만 읽을 수 있게 저장했습니다) ==

아래 두 값은 지금 한 번만 보여 줍니다. 비밀번호 관리 앱에 저장하세요.

  첫 관리자  아이디: $ADMIN_USER
             비밀번호: $ADMIN_PASSWORD
  백업 암호: $BACKUP_PASSPHRASE   ← 잃으면 백업을 열 수 없습니다

다음 순서
  1) docker compose -f docker-compose.prod.yml up -d
  2) https://www.$DOMAIN/admin 에서 위 계정으로 로그인 → 관리자 관리에서 비밀번호 변경
  3) $OUT 에서 ADMIN_BOOTSTRAP_ 두 줄 삭제 → docker compose -f docker-compose.prod.yml up -d
EOF
