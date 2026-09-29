# syntax=docker/dockerfile:1

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 의존성 정의만 먼저 복사해서 받아 두면, 소스만 바뀐 경우 이 층을 캐시로 재사용한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# 테스트는 CI(GitHub Actions)에서 Testcontainers로 돌리므로 이미지 빌드에서는 건너뛴다
RUN ./gradlew bootJar --no-daemon -x test \
    && cp "$(ls build/libs/*.jar | grep -v plain)" app.jar

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# root가 아닌 사용자로 실행
RUN useradd --system --uid 1001 fitmate
COPY --from=build --chown=fitmate:fitmate /app/app.jar app.jar
USER fitmate

# 컨테이너 메모리 한도에 맞춰 힙을 잡는다 (Railway 등 작은 인스턴스에서 OOM 방지)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul"
# Railway는 PORT 환경 변수를 주고, application.yml이 server.port=${PORT}로 받는다
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
