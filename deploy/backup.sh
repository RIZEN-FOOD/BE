#!/usr/bin/env bash
# 운영 백업 → 암호화(gpg AES256) → Lightsail 저장소 버킷.
#   - DB      : 매 실행(6시간마다)
#   - 업로드 사진 : 하루 1번 (UTC 18시 = 한국 03시 실행분)
#   - 두 달 지난 달의 백업은 지운다 (버킷 5GB 요금제 안에 머물게)
#
# 서버 전체는 Lightsail 자동 스냅샷(매일)으로도 따로 보관된다. 이 스크립트는 서버를 통째로
# 잃었을 때 새 서버에서 DB·사진만 되살리기 위한 것이다.
#
# 설치 (서버에서 한 번)
#   chmod +x /opt/rizen/backup.sh
#   crontab -e  →  0 */6 * * * /opt/rizen/backup.sh >> /opt/rizen/backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")"
set -a
# shellcheck disable=SC1091
. ./.env
set +a

: "${BACKUP_PASSPHRASE:?.env 에 BACKUP_PASSPHRASE 가 없다}"
: "${BACKUP_BUCKET:?.env 에 BACKUP_BUCKET 이 없다}"
: "${BACKUP_ACCESS_KEY_ID:?.env 에 BACKUP_ACCESS_KEY_ID 가 없다}"
: "${BACKUP_SECRET_ACCESS_KEY:?.env 에 BACKUP_SECRET_ACCESS_KEY 가 없다}"
REGION="${BACKUP_REGION:-ap-northeast-2}"
COMPOSE="docker compose -f docker-compose.prod.yml"

TS="$(date -u +%Y%m%dT%H%M%SZ)"
PREFIX="rizenfood/$(date -u +%Y/%m)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

encrypt() { # stdin → $1 (암호는 인자가 아니라 파일 디스크립터 3 으로 넘긴다 — ps 에 안 보이게)
  gpg --batch --yes --pinentry-mode loopback --symmetric --cipher-algo AES256 \
      --passphrase-fd 3 -o "$1" 3<<<"$BACKUP_PASSPHRASE"
}

s3() {
  docker run --rm -v "$WORK":/work \
    -e AWS_ACCESS_KEY_ID="$BACKUP_ACCESS_KEY_ID" \
    -e AWS_SECRET_ACCESS_KEY="$BACKUP_SECRET_ACCESS_KEY" \
    -e AWS_DEFAULT_REGION="$REGION" \
    amazon/aws-cli "$@"
}

# 1) DB
$COMPOSE exec -T db pg_dump -U "${DB_USERNAME:-rizen}" --no-owner --no-privileges rizenfood \
  | gzip -9 | encrypt "$WORK/db-$TS.sql.gz.gpg"
test -s "$WORK/db-$TS.sql.gz.gpg"
s3 s3 cp "/work/db-$TS.sql.gz.gpg" "s3://$BACKUP_BUCKET/$PREFIX/" --only-show-errors
echo "$(date -u +%FT%TZ) DB 백업 완료 ($(du -h "$WORK/db-$TS.sql.gz.gpg" | cut -f1))"

# 2) 업로드 사진 — 하루 1번
if [ "$(date -u +%H)" = "18" ] || [ "${FORCE_UPLOADS:-}" = "1" ]; then
  $COMPOSE exec -T api tar czf - -C /app/uploads . | encrypt "$WORK/uploads-$TS.tar.gz.gpg"
  test -s "$WORK/uploads-$TS.tar.gz.gpg"
  s3 s3 cp "/work/uploads-$TS.tar.gz.gpg" "s3://$BACKUP_BUCKET/$PREFIX/" --only-show-errors
  echo "$(date -u +%FT%TZ) 사진 백업 완료 ($(du -h "$WORK/uploads-$TS.tar.gz.gpg" | cut -f1))"
fi

# 3) 두 달 전 달 정리
OLD="rizenfood/$(date -u -d "$(date -u +%Y-%m-15) -2 month" +%Y/%m)/"
s3 s3 rm "s3://$BACKUP_BUCKET/$OLD" --recursive --only-show-errors || true
