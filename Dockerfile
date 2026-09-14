# syntax=docker/dockerfile:1
#
# API 서버 이미지. x86(amd64)·ARM(arm64) 둘 다 빌드한다 (.github/workflows/build-image.yml).
#   docker buildx build --platform linux/amd64,linux/arm64 -t ghcr.io/rizen-food/be:latest .
#
# 런타임은 glibc 기반(Ubuntu) Temurin 이다. WebP 인코더(webp-imageio)가 네이티브 라이브러리를
# 쓰므로 musl 기반(alpine) 이미지는 쓰지 않는다.

# ── 빌드 ──────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# 의존성 층을 먼저 만들어 소스만 바뀌면 캐시를 재사용한다.
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
# 윈도우에서 체크아웃된 줄바꿈(CRLF)이면 리눅스에서 gradlew 가 실행되지 않는다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /app.jar

# ── 실행 ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar

# 컨테이너 메모리 한도(docker-compose 의 mem_limit)의 70% 를 힙으로 쓴다.
# 메모리가 바닥나면 어정쩡하게 버티지 말고 종료해 재시작 정책이 다시 띄우게 한다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
