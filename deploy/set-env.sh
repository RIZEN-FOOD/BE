#!/usr/bin/env bash
# 운영 .env 의 키 하나만 다시 넣는다 (입력은 화면에 보이지 않는다).
#
#   cd /opt/rizen && bash set-env.sh KAKAO_CLIENT_SECRET
#
# 바꾼 뒤 반영: docker compose -f docker-compose.prod.yml up -d
# ★ DB_PASSWORD·PHONE_ENC_KEY 처럼 이미 데이터에 쓰인 값은 이 스크립트로 바꾸지 않는다.
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env}"
KEY="${1:-}"

if ! [[ "$KEY" =~ ^[A-Z][A-Z0-9_]*$ ]]; then
  echo "사용법: bash set-env.sh 키이름   (예: bash set-env.sh KAKAO_CLIENT_SECRET)"
  exit 1
fi
case "$KEY" in
  DB_PASSWORD|DB_USERNAME|PHONE_ENC_KEY|BACKUP_PASSPHRASE)
    echo "$KEY 는 이미 저장된 데이터에 쓰인 값이라 바꾸면 안 됩니다."
    exit 1 ;;
esac
[ -f "$ENV_FILE" ] || { echo "$ENV_FILE 가 없습니다. 먼저 make-env.sh 를 실행하세요."; exit 1; }
grep -q "^$KEY=" "$ENV_FILE" || { echo "$ENV_FILE 에 $KEY 항목이 없습니다."; exit 1; }

read -r -s -p "$KEY 값 붙여넣기 후 Enter (화면에 안 보이는 게 정상): " v
echo
# 복사할 때 딸려 온 앞뒤 공백·줄바꿈 제거
v="$(printf '%s' "$v" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
if [ -z "$v" ]; then
  echo "빈 값이라 바꾸지 않았습니다."
  exit 1
fi
if [[ "$v" == *"'"* ]]; then
  echo "작은따옴표(')가 들어간 값은 받을 수 없습니다."
  exit 1
fi

tmp="$(mktemp "${ENV_FILE}.XXXXXX")"
chmod 600 "$tmp"
while IFS= read -r line || [ -n "$line" ]; do
  if [[ "$line" == "$KEY="* ]]; then
    printf "%s='%s'\n" "$KEY" "$v"
  else
    printf '%s\n' "$line"
  fi
done < "$ENV_FILE" > "$tmp"
mv "$tmp" "$ENV_FILE"

echo "$KEY 저장 완료 (${#v}자)"
