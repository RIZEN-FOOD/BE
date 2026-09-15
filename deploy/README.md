# 운영 서버 셋업 — AWS Lightsail 2GB (서울)

구성 (2026-09-15 결정 — 하루 방문 100~200명 기준, 커지면 스냅샷으로 4GB 이상에 옮긴다)

    손님 ─ https://www.도메인 ─> Lightsail 서버 한 대 (Docker)
                                  caddy  : HTTPS 자동 인증서 · 입구
                                  web    : 쇼핑몰 화면 (Next.js)
                                  api    : 주문·결제·관리 (Spring Boot)
                                  db     : PostgreSQL 16 (인터넷 비공개)
                                  uploads: 상품 사진 (서버 디스크)
    백업 ─ backup.sh(6시간마다, 암호화) → Lightsail 저장소 버킷
         ─ Lightsail 자동 스냅샷(매일, 서버 통째)

도메인 DNS 는 가비아에서 바로 서버 고정 IP 로 향한다 (Cloudflare 안 씀 — 한국 접속이 해외 거점으로 돌지 않게).

월 비용: 서버 $12 + 스냅샷 약 $1 + 버킷 $1 + 부가세 ≈ $15 (약 2.2만원) + 가비아 도메인.

---

## 1. Lightsail 에서 만들 것 (콘솔, 서울 리전)

| 무엇 | 설정 |
|---|---|
| 인스턴스 | Seoul · Linux/Unix · **OS Only → Ubuntu 24.04 LTS** · Dual-stack · **$12 (2 GB, 2 vCPU, 60 GB)** · 이름 `rizen-web` |
| 고정 IP | Networking → Create static IP → `rizen-web` 에 연결 (연결돼 있으면 무료) |
| 방화벽 (IPv4·IPv6 모두) | 인스턴스 → Networking: **HTTP 80, HTTPS 443 허용**, **SSH 22 는 개발자 IP 만** |
| 자동 스냅샷 | 인스턴스 → Snapshots → Automatic snapshots **On** (매일, 7개 보관) |
| 저장소 버킷 | Storage → Create bucket · Seoul · **$1 (5 GB)** · 비공개 · Permissions → Access keys 생성 → `.env` 의 BACKUP_* |

## 2. 서버 기본 설정 (처음 한 번, SSH)

    # 작업용 사용자 (Lightsail 기본 사용자 ubuntu 를 써도 된다)
    sudo adduser deploy && sudo usermod -aG sudo deploy
    # 로컬 PC 공개키를 /home/deploy/.ssh/authorized_keys 에 넣는다

    # 비밀번호 로그인·root 로그인 끄기 — /etc/ssh/sshd_config
    #   PasswordAuthentication no
    #   PermitRootLogin no
    sudo systemctl restart ssh

    # 보안 업데이트 자동 설치
    sudo apt install -y unattended-upgrades && sudo dpkg-reconfigure -plow unattended-upgrades

    # 스왑 2GB — 2GB 서버에서 필수 (큰 사진 업로드 등 순간 메모리 튐 대비)
    sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
    sudo mkswap /swapfile && sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

    # Docker
    curl -fsSL https://get.docker.com | sudo sh
    sudo usermod -aG docker deploy

방화벽은 Lightsail 콘솔 방화벽을 기준으로 한다(1번 표). 서버 안 ufw 를 따로 켜려면 같은 규칙(22·80·443)으로 맞춘다.

## 3. 도메인 연결 (가비아 DNS)

가비아 → DNS 관리 → 레코드 2개 (값은 Lightsail 고정 IP)

| 타입 | 호스트 | 값 |
|---|---|---|
| A | @ | 고정 IP |
| A | www | 고정 IP |

반영 확인: `nslookup www.도메인` 이 고정 IP 를 돌려주면 다음 단계로 (보통 수 분~수 시간).

## 4. 배포

    sudo mkdir -p /opt/rizen && sudo chown deploy /opt/rizen && cd /opt/rizen
    # deploy/docker-compose.prod.yml, deploy/Caddyfile, deploy/backup.sh, deploy/.env.example 을 이 폴더로 복사
    cp .env.example .env && chmod 600 .env     # 값 채우기 (DB_PASSWORD·BACKUP_PASSPHRASE 는 길고 무작위로)

    # GitHub Actions 에서 BE "Build API image", FE "Build web image" 를 먼저 실행해 이미지를 만든다
    echo <read:packages 토큰> | docker login ghcr.io -u <GitHub 아이디> --password-stdin
    docker compose -f docker-compose.prod.yml pull
    docker compose -f docker-compose.prod.yml up -d
    docker compose -f docker-compose.prod.yml logs -f caddy api    # 인증서 발급, "Started ApiApplication" 확인

- DB 는 첫 기동 때 비어 있고, API 가 뜨면서 Flyway 가 테이블을 만든다.
- 첫 기동 후: 관리자 로그인 확인 → `.env` 에서 `ADMIN_BOOTSTRAP_*` 두 줄 삭제 → `up -d` 로 재시작.
- 업데이트: Actions 에서 이미지 빌드 → 서버에서 `pull` → `up -d` (처리 중 요청은 마치고 내려간다).

## 5. 백업

    chmod +x /opt/rizen/backup.sh
    FORCE_UPLOADS=1 /opt/rizen/backup.sh      # 한 번 손으로: 버킷에 db-…, uploads-… 파일이 생기는지
    crontab -e
    # 0 */6 * * * /opt/rizen/backup.sh >> /opt/rizen/backup.log 2>&1

`BACKUP_PASSPHRASE` 는 서버 밖(비밀번호 관리 앱)에도 따로 보관한다. 서버가 통째로 사라지면 이 암호가 있어야 백업을 연다.

## 6. 기존 로컬 데이터 옮기기 (처음 한 번)

- 사진: 로컬 `BE/uploads/` 를 서버 업로드 볼륨으로 복사한다.

      scp -r BE/uploads/* deploy@<고정IP>:/tmp/uploads/
      docker compose -f docker-compose.prod.yml cp /tmp/uploads/. api:/app/uploads/

- DB: 로컬 개발 DB 에는 테스트 주문·회원이 섞여 있다. 운영은 **빈 DB 로 시작**하고 관리자 화면에서 상품·설정을 입력하는 것을 기본으로 한다.

## 7. 오픈 전 확인 목록

- [ ] `https://www.도메인` 화면, `https://도메인` → www 로 넘어가는지, 자물쇠(인증서) 정상
- [ ] 서버 공인 IP 로 3000·8080·5432 직접 접속이 **안 되는지**
- [ ] **IP 위조 테스트**: `X-Real-IP: 1.2.3.4`, `X-Forwarded-For: 1.2.3.4` 를 넣고 관리자 로그인 → 감사 로그에 1.2.3.4 가 **아닌** 실제 IP
- [ ] 로그인 11회 연속 → 429 "요청이 너무 많습니다"
- [ ] 쿠키에 `Secure` (브라우저 개발자도구)
- [ ] 관리자에서 사진 업로드 → 화면에 보이는지, 재시작 후에도 남는지
- [ ] `FORCE_UPLOADS=1 backup.sh` → 버킷 파일 생성 → **복구 시험**(아래)
- [ ] 자동 스냅샷 1개 이상 생성됐는지
- [ ] 가동 감시(UptimeRobot 등): `https://www.도메인/api/products` — 멈추면 휴대폰 알림
- [ ] `docker stats` 로 메모리 여유 확인 (지속적으로 80% 넘으면 4GB 로)

## 8. 복구

DB

    aws s3 cp s3://<버킷>/rizenfood/<년>/<월>/db-<시각>.sql.gz.gpg .
    gpg --decrypt db-<시각>.sql.gz.gpg | gunzip \
      | docker compose -f docker-compose.prod.yml exec -T db psql -U rizen -d rizenfood

사진

    gpg --decrypt uploads-<시각>.tar.gz.gpg \
      | docker compose -f docker-compose.prod.yml exec -T api tar xzf - -C /app/uploads

- 실제 운영 DB 에 덮어쓰기 전에 **빈 DB(새 컨테이너)에 먼저 복구해 확인**한다.
- 서버를 통째로 잃었으면: **Lightsail 스냅샷에서 새 인스턴스를 만드는 것**이 가장 빠르다(고정 IP 를 새 인스턴스로 옮김). 스냅샷이 없으면 1~4단계 → `stop api` → 위 복구 → `start api`.

## 9. 사양 올리기 (2GB → 4GB)

인스턴스 → Snapshots → 스냅샷 생성 → 그 스냅샷으로 **더 큰 요금제($24, 4GB)** 인스턴스 생성 → 고정 IP 를 새 인스턴스로 옮기기 → `docker-compose.prod.yml` 의 mem_limit 을 올리고 `up -d`. 주소·설정은 그대로다.
