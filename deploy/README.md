# 운영 서버 셋업

구성 (2026-09-14 결정)

    사용자 ─> www.도메인 (Cloudflare Workers: 프론트)
                 │ /api/* 중계 (같은 도메인이라 로그인 쿠키가 오간다)
                 ▼
             api.도메인 ─ Cloudflare Tunnel ─> 이 서버 (Docker: api + cloudflared)
                                                  │  열린 포트 없음
                                                  ▼
                                             Supabase PostgreSQL (서울)
             img.도메인 ─ Cloudflare R2 (상품 이미지)
             GitHub Actions ─ 이미지 빌드 · 매일 DB 백업(암호화) → R2

서버: 리눅스 2GB (카페24 가상서버 또는 Lightsail). Ubuntu 22.04/24.04 기준.

---

## 1. 서버 기본 보안 (처음 한 번)

    # 작업용 사용자, SSH 키 로그인
    adduser deploy && usermod -aG sudo deploy
    # 로컬 PC 공개키를 /home/deploy/.ssh/authorized_keys 에 넣는다

    # 비밀번호 로그인·root 로그인 끄기 — /etc/ssh/sshd_config
    #   PasswordAuthentication no
    #   PermitRootLogin no
    sudo systemctl restart ssh

    # 방화벽: 들어오는 연결은 SSH 만. (API 는 Tunnel 이 안에서 밖으로 연결하므로 열 필요 없다)
    sudo ufw default deny incoming
    sudo ufw default allow outgoing
    sudo ufw allow OpenSSH
    sudo ufw enable

    # 보안 업데이트 자동 설치
    sudo apt install -y unattended-upgrades && sudo dpkg-reconfigure -plow unattended-upgrades

    # 스왑 1GB — 이미지 여러 장 업로드처럼 순간적으로 메모리가 튈 때 강제 종료를 막는다
    sudo fallocate -l 1G /swapfile && sudo chmod 600 /swapfile
    sudo mkswap /swapfile && sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

    # Docker
    curl -fsSL https://get.docker.com | sudo sh
    sudo usermod -aG docker deploy

## 2. 외부 서비스 준비

| 서비스 | 할 일 |
|---|---|
| Supabase | 서울 리전 프로젝트 생성 → Settings → Database → **Session pooler(5432)** 연결 문자열. Network restrictions 가 있으면 서버 IP·GitHub Actions 만 허용 |
| Cloudflare R2 | 버킷 3개: `rizenfood-images`(사용자 지정 도메인 `img.도메인` 연결), `rizenfood-web-opennext-cache`(프론트 캐시), `rizenfood-db-backup`(비공개, 수명 주기 30일 삭제). API 토큰: 이미지 버킷 읽기·쓰기 / 백업 버킷 쓰기 — **따로** 발급 |
| Cloudflare Tunnel | Zero Trust → Networks → Tunnels → 생성 → 토큰 복사. Public hostname `api.도메인` → `http://api:8080` |
| Cloudflare Access | Zero Trust → Access → Applications → Self-hosted: `www.도메인/admin*`, `www.도메인/api/admin*` 두 경로. 정책: 관리자 이메일만 허용(일회용 코드) |
| GitHub (BE 저장소) | Actions 시크릿·변수: `.github/workflows/db-backup.yml` 머리말 참고 |

## 3. 배포

    sudo mkdir -p /opt/rizen && sudo chown deploy /opt/rizen && cd /opt/rizen
    # 이 폴더에 deploy/docker-compose.prod.yml 과 deploy/.env.example 을 복사
    cp .env.example .env && chmod 600 .env     # 값 채우기

    # GitHub Actions 의 "Build API image" 를 먼저 한 번 실행해 이미지를 만든다
    echo <read:packages 토큰> | docker login ghcr.io -u <GitHub 아이디> --password-stdin
    docker compose -f docker-compose.prod.yml pull
    docker compose -f docker-compose.prod.yml up -d
    docker compose -f docker-compose.prod.yml logs -f api      # "Started ApiApplication" 확인

    # 첫 기동 후: 관리자 로그인 확인 → .env 에서 ADMIN_BOOTSTRAP_* 두 줄 삭제 → up -d 로 재시작

업데이트: Actions 에서 이미지 빌드 → 서버에서 `pull` → `up -d` (처리 중 요청은 마치고 내려간다).

## 4. 기존 로컬 이미지 옮기기 (처음 한 번)

DB 에는 이미지 "키"만 저장돼 있어 파일만 같은 키로 올리면 된다.

    aws s3 sync BE/uploads/ s3://rizenfood-images/ \
      --endpoint-url https://<계정ID>.r2.cloudflarestorage.com

## 5. 오픈 전 확인 목록

- [ ] `https://api.도메인/healthz` 200, 서버 공인 IP 로 8080 직접 접속은 **안 되는지**
- [ ] **IP 위조 테스트**: `X-Forwarded-For: 1.2.3.4`, `X-Real-IP: 1.2.3.4` 를 넣고 관리자 로그인 → 감사 로그에 1.2.3.4 가 **아닌** 실제 IP 가 남는지
- [ ] 로그인 11회 연속 → 429 "요청이 너무 많습니다"
- [ ] `/admin` 접속 시 Cloudflare Access 이메일 인증이 먼저 뜨는지
- [ ] 쿠키에 `Secure` 가 붙는지 (브라우저 개발자도구)
- [ ] 이미지 업로드 → `img.도메인` 주소로 보이는지
- [ ] DB 백업 워크플로 수동 실행 → R2 에 파일 생성 → **복구 시험**(아래)
- [ ] 가동 감시(UptimeRobot 등): `https://api.도메인/healthz`, `https://www.도메인` — 멈추면 휴대폰 알림

## 6. 복구

    aws s3 cp s3://rizenfood-db-backup/<경로>.sql.gz.gpg . --endpoint-url https://<계정ID>.r2.cloudflarestorage.com
    gpg --decrypt <파일>.sql.gz.gpg | gunzip | psql "<복구할 DB 연결 문자열>"

실제 운영 DB 에 덮어쓰기 전에 **빈 Supabase 프로젝트에 먼저 복구해 확인**한다.
