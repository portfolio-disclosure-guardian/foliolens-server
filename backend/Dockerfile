# syntax=docker/dockerfile:1

# ----------------------------------------
# 1단계: Spring Boot JAR 빌드
# ----------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

COPY src ./src

RUN ./gradlew clean bootJar --no-daemon

# plain JAR가 아닌 Spring Boot 실행 JAR를 application.jar로 복사
RUN JAR_FILE="$(find build/libs -maxdepth 1 \
    -type f \
    -name '*.jar' \
    ! -name '*-plain.jar' \
    -print \
    -quit)" \
    && test -n "${JAR_FILE}" \
    && cp "${JAR_FILE}" /workspace/application.jar

# ----------------------------------------
# 2단계: 실제 실행 이미지
# ----------------------------------------
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring spring

COPY --from=builder \
    --chown=spring:spring \
    /workspace/application.jar \
    /app/application.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/application.jar"]