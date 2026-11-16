# syntax=docker/dockerfile:1
# Multi-stage build for the Trebol backend.
#   Stage 1 compiles the app with Maven and the full JDK.
#   Stage 2 is a small JRE-only image that runs it as a non-root user.
# Tests are not run here: CI runs them (Testcontainers needs Docker, which a build container doesn't have).
#
# APP_SOURCE chooses where the app comes from:
#   source   (default, used by CI) compile inside Docker
#   prebuilt use target/*.war that you already built with `mvn package -DskipTests`
#            (for networks where Maven downloads fail inside Docker)
ARG APP_SOURCE=source

############################
# Stage 1: build
############################
FROM maven:3.9-eclipse-temurin-17 AS build-source
WORKDIR /build

# Maven settings for slow or flaky networks: retry failed downloads and allow longer reads
ENV MAVEN_OPTS="-Daether.connector.http.retryHandler.count=10 -Daether.connector.connectTimeout=60000 -Daether.connector.requestTimeout=300000"

# Download dependencies first, in their own layer, so they are only re-downloaded when pom.xml changes.
# The cache mount keeps ~/.m2 between builds, so a failed build resumes instead of starting over.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package -DskipTests \
 && cp target/*.war /build/app.war

# Alternative stage 1: reuse a WAR built on the host
FROM eclipse-temurin:17-jre-alpine AS build-prebuilt
COPY target/*.war /build/app.war

FROM build-${APP_SOURCE} AS build

############################
# Stage 2: runtime
############################
FROM eclipse-temurin:17-jre-alpine

# Pull the latest Alpine security fixes on every build, so OS packages (e.g. zlib,
# CVE-2026-4738 found by the ECR scan) don't wait for a new base-image release
RUN apk upgrade --no-cache

# Unprivileged user: if the app is ever compromised, it has no root rights in the container
RUN addgroup -S trebol && adduser -S -G trebol -H -s /sbin/nologin trebol

WORKDIR /app
COPY --from=build --chown=trebol:trebol /build/app.war /app/app.war

USER trebol

# Size the heap from the container's memory limit (the ECS task size), not the host's,
# and restart cleanly instead of limping on after an out-of-memory error
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# Container-level check for docker compose; on AWS the load balancer checks /actuator/health/readiness
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

# Exec form: java runs as PID 1 and receives SIGTERM directly, so graceful shutdown works
ENTRYPOINT ["java", "-jar", "/app/app.war"]
