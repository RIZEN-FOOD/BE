#!/usr/bin/env bash
# 백업 복구 시험 — 가장 최근 DB 백업을 «빈 임시 DB»에 되살려 보고, 운영 DB 와 건수를 비교한다.
#
#   cd /opt/rizen && bash restore-test.sh
#
# ★ 운영 DB 는 읽기(건수 세기)만 한다. 복구는 이름이 rizen-restore-test 인 임시 컨테이너에 하고, 끝나면 지운다.
# ★ 백업은 6시간마다라 최근 주문 몇 건은 차이가 날 수 있다(정상). 0 건이거나 크게 다르면 백업이 잘못된 것이다.
# 백업 방식은 backup.sh, 실제 복구 절차는 README §8.
set -euo pipefail

cd "$(dirname "$0")"
set -a
# shellcheck disable=SC1091
. ./.env
set +a

: "${BACKUP_PASSPHRASE:?.env 에 BACKUP_PASSPHRASE 가 없다}"
: "${BACKUP_BUCKET:?.env 에 BACKUP_BUCKET 이 없다}"
REGION="${BACKUP_REGION:-ap-northeast-2}"
COMPOSE="docker compose -f docker-compose.prod.yml"
TEST_DB=rizen-restore-test

WORK="$(mktemp -d)"
trap 'docker rm -f "$TEST_DB" >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT

s3() {
  docker run --rm -v "$WORK":/work \
    -e AWS_ACCESS_KEY_ID="$BACKUP_ACCESS_KEY_ID" \
    -e AWS_SECRET_ACCESS_KEY="$BACKUP_SECRET_ACCESS_KEY" \
    -e AWS_DEFAULT_REGION="$REGION" \
    amazon/aws-cli "$@"
}

# 1) 가장 최근 DB 백업 받기
LATEST="$(s3 s3 ls "s3://$BACKUP_BUCKET/rizenfood/" --recursive | awk '{print $4}' \
  | grep '/db-.*\.sql\.gz\.gpg$' | sort | tail -1 || true)"
if [ -z "$LATEST" ]; then
  echo "✗ 버킷에 DB 백업이 없습니다. backup.sh 가 돌고 있는지(crontab -l, backup.log) 확인하세요."
  exit 1
fi
echo "가장 최근 백업: $LATEST"
s3 s3 cp "s3://$BACKUP_BUCKET/$LATEST" /work/db.sql.gz.gpg --only-show-errors

# 2) 빈 임시 DB (운영과 같은 postgres 16, 네트워크·포트 없이)
docker rm -f "$TEST_DB" >/dev/null 2>&1 || true
docker run -d --name "$TEST_DB" --network none \
  -e POSTGRES_PASSWORD=restore-test -e POSTGRES_DB=rizenfood postgres:16 >/dev/null
for _ in $(seq 1 60); do
  docker exec "$TEST_DB" pg_isready -U postgres -d rizenfood >/dev/null 2>&1 && break
  sleep 1
done

# 3) 복호화 → 압축 해제 → 임시 DB 에 복구 (오류가 하나라도 나면 멈춘다)
gpg --batch --quiet --pinentry-mode loopback --passphrase-fd 3 --decrypt "$WORK/db.sql.gz.gpg" 3<<<"$BACKUP_PASSPHRASE" \
  | gunzip \
  | docker exec -i "$TEST_DB" psql -q -U postgres -d rizenfood -v ON_ERROR_STOP=1 >/dev/null
echo "✓ 복구 성공 (복호화·압축 해제·SQL 모두 오류 없음)"

# 4) 운영과 건수 비교
Q="SELECT 'orders', count(*) FROM orders
   UNION ALL SELECT 'order_item', count(*) FROM order_item
   UNION ALL SELECT 'product', count(*) FROM product
   UNION ALL SELECT 'member', count(*) FROM member
   UNION ALL SELECT 'DB 버전(마이그레이션)', max(installed_rank) FROM flyway_schema_history"
echo
printf '%-24s %10s %10s\n' "항목" "백업" "운영"
paste -d'|' \
  <(docker exec "$TEST_DB" psql -U postgres -d rizenfood -At -F'|' -c "$Q") \
  <($COMPOSE exec -T db psql -U "${DB_USERNAME:-rizen}" -d rizenfood -At -F'|' -c "$Q" | cut -d'|' -f2) \
  | while IFS='|' read -r name backup prod; do printf '%-24s %10s %10s\n' "$name" "$backup" "$prod"; done
echo
echo "임시 DB 를 지웁니다. (운영 DB 는 그대로입니다)"
