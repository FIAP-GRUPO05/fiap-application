# Multi-stage Dockerfile for Spring Boot (Java 21)
# Builder: uses project wrapper to produce a fat jar
FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /work

# Copy gradle wrapper and project files
COPY gradlew .
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY src src

# Make wrapper executable and build jar (skip tests for faster local builds)
RUN chmod +x ./gradlew && \
    ./gradlew bootJar --no-daemon -x test

# Runtime image: slim JRE
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

# curl is needed by the container healthcheck; run as non-root (least privilege)
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    useradd --system --no-create-home --uid 1001 spring
COPY --from=builder /work/build/libs/*.jar /app/app.jar
USER spring

ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS
ENV SPRING_PROFILES_ACTIVE=default

EXPOSE 8080
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
