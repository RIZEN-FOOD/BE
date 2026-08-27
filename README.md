# BE — 라이즌푸드 API

Spring Boot 3.5 / Java 21 / PostgreSQL 16 / Flyway

## 실행

레포 루트에서 DB 를 먼저 띄운다.

    docker compose up -d

그 다음 이 디렉터리에서:

    ./gradlew bootRun

`bootRun` 은 레포 루트의 `.env` 를 읽어 환경변수로 넣는다 (build.gradle 참조).

## 확인

    curl -i http://localhost:8080/healthz

## 스키마

모든 스키마 변경은 `src/main/resources/db/migration` 의 Flyway SQL 로만 한다.
Hibernate 의 `ddl-auto` 는 `validate` 로 고정되어 있어 테이블을 자동 생성하지 않는다.
