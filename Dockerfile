# syntax=docker/dockerfile:1

# Base images are pinned by tag and digest for reproducible builds. To upgrade, resolve the new digest
# and update both values together.
ARG JDK_IMAGE=eclipse-temurin:21.0.12_8-jdk-alpine-3.23@sha256:52ca1500049e2e2e7cee85fc4cd8689108792dc2bde18161b7f9236f609f0dea
ARG JRE_IMAGE=eclipse-temurin:21.0.12_8-jre-alpine-3.23@sha256:1f165f3a75613b80cc803a2ccc4d865e5c8b845b97f692988d75c4a026609e61

# ---- Build stage: JDK and Maven. Discarded after the JAR is produced. ----
FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

# The Maven Wrapper (only-script distribution) needs unzip to extract Maven.
RUN apk add --no-cache unzip

# Copy the build descriptors first so the dependency layer is cached independently of source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/

# Tests run here on purpose: an image cannot be produced from a commit whose tests fail.
# The Maven repository and wrapper distribution are kept in a BuildKit cache mount, not in the image.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp package \
    && cp target/*.jar /workspace/app.jar

# ---- Runtime stage: JRE only, no Maven, no compiler, no source code. ----
FROM ${JRE_IMAGE} AS runtime

# Passed by CI (commit SHA) so each image can be traced back to its source revision.
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="ai-travel-companion-api" \
      org.opencontainers.image.revision="${VCS_REF}"

# Fixed numeric UID/GID so the non-root user can be verified by ECS and orchestrators.
RUN addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/app.jar /app/app.jar

# Defaults are development-safe: mock attractions, AI disabled, port 8080. No credentials are set here.
# Override any value at run time with -e or the ECS task definition.
ENV SERVER_PORT=8080 \
    ATTRACTION_PROVIDER=mock \
    TRAVEL_AI_ENABLED=false \
    JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080

# Uses busybox wget, which ships with Alpine. Docker-only: ECS and ALB use their own health checks
# (see docs/DOCKER_AND_CI.md).
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

# Exec form: java is PID 1 and receives SIGTERM directly, so Spring Boot's graceful shutdown runs.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
