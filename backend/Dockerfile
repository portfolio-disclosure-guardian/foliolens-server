FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY build.gradle settings.gradle ./

RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew

COPY src src

RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring \
    && adduser -S spring -G spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /workspace/build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
