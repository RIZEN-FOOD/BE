#!/usr/bin/env bash
# 운영 자동 반영 — systemd 타이머가 2분마다 실행한다 (rizen-deploy.timer).
#
#   GitHub Actions: main 푸시 → 테스트 → 이미지(latest) 올림
#   이 스크립트   : 새 latest 가 있으면 받아서 교체 → 상태 확인 → 실패하면 이전 이미지로 되돌림
#
# 되돌린 이미지는 bad-images 에 적어 두고 다시 올리지 않는다. 고친 코드를 다시 푸시하면
# 새 이미지가 되어 자동으로 반영된다.
#
# 손으로 실행:  bash /opt/rizen/auto-deploy.sh
# 기록 보기  :  tail -f /opt/rizen/deploy.log
# 잠시 멈춤  :  touch /opt/rizen/deploy.paused   (다시 켜려면 파일 삭제)
set -uo pipefail

DIR=/opt/rizen
COMPOSE=(docker compose -f "$DIR/docker-compose.prod.yml" --project-directory "$DIR")
STATE="$DIR/.deploy"
LOG="$DIR/deploy.log"
SERVICES=(api web)

mkdir -p "$STATE"
touch "$STATE/bad-images"

log() { printf '%s %s\n' "$(date '+%F %T')" "$*" >> "$LOG"; }

[ -f "$DIR/deploy.paused" ] && exit 0

# 동시에 두 번 돌지 않게
exec 9> "$STATE/lock"
flock -n 9 || exit 0

DOMAIN="$(grep -E '^DOMAIN=' "$DIR/.env" | cut -d= -f2- | tr -d "'\"")"

image_of() {   # compose 서비스가 쓰는 이미지 이름 (태그까지)
  "${COMPOSE[@]}" config --format json 2>/dev/null     | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"][sys.argv[1]]["image"])' "$1" 2>/dev/null
}
running_id() { # 지금 돌고 있는 컨테이너의 이미지 ID
  local c
  c="$("${COMPOSE[@]}" ps -q "$1" 2>/dev/null | head -n1)"
  [ -n "$c" ] && docker inspect -f '{{.Image}}' "$c" 2>/dev/null
}
local_id() { docker image inspect -f '{{.Id}}' "$1" 2>/dev/null; }
revision() { docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$1" 2>/dev/null | cut -c1-7; }

healthy() {    # 손님이 들어오는 길(Caddy)로 확인한다
  local svc="$1" url
  case "$svc" in
    api) url="https://www.$DOMAIN/api/products?size=1" ;;
    web) url="https://www.$DOMAIN/" ;;
  esac
  for _ in $(seq 1 36); do   # 최대 3분
    if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
          --resolve "www.$DOMAIN:443:127.0.0.1" "$url")" = "200" ]; then
      return 0
    fi
    sleep 5
  done
  return 1
}

for svc in "${SERVICES[@]}"; do
  img="$(image_of "$svc")"
  [ -n "$img" ] || { log "[$svc] 이미지 이름을 못 읽음"; continue; }

  if ! docker pull -q "$img" > /dev/null 2>> "$LOG"; then
    log "[$svc] 이미지 받기 실패 (GitHub 로그인·네트워크 확인)"
    continue
  fi

  new_id="$(local_id "$img")"
  cur_id="$(running_id "$svc")"
  [ -n "$new_id" ] || continue
  [ "$new_id" = "$cur_id" ] && continue
  grep -qx "$new_id" "$STATE/bad-images" && continue

  log "[$svc] 새 이미지 $(revision "$img") 반영 시작"
  if [ -n "$cur_id" ]; then
    docker tag "$cur_id" "${img%:*}:previous"
  fi

  "${COMPOSE[@]}" up -d --no-deps "$svc" >> "$LOG" 2>&1

  if healthy "$svc"; then
    log "[$svc] 반영 완료 $(revision "$img")"
  else
    log "[$svc] 상태 확인 실패 → 이전 이미지로 되돌림"
    echo "$new_id" >> "$STATE/bad-images"
    "${COMPOSE[@]}" logs --tail 60 "$svc" >> "$LOG" 2>&1
    if [ -n "$cur_id" ]; then
      docker tag "$cur_id" "$img"
      "${COMPOSE[@]}" up -d --no-deps "$svc" >> "$LOG" 2>&1
      if healthy "$svc"; then
        log "[$svc] 되돌리기 완료 $(revision "$img")"
      else
        log "[$svc] ★ 되돌린 뒤에도 응답 없음 — 개발자 확인 필요"
      fi
    fi
  fi
done

# 쓰지 않는 옛 이미지 정리 (latest·previous 태그가 붙은 것은 남는다)
docker image prune -f > /dev/null 2>&1 || true
