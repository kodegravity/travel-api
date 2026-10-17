# Docker and CI

This document covers the production container image and the GitHub Actions backend CI pipeline. The CI pipeline pushes the scanned image to AWS ECR on master pushes. ECS deployment is not implemented yet.

## Prerequisites

- Docker Engine or Docker Desktop with BuildKit (default in current versions).
- JDK 21 only if you want to run Maven outside Docker. The Maven Wrapper downloads Maven 3.9.9.

## Build the image

```bash
docker build -t ai-travel-companion-api:local .
```

The build compiles the code and runs the full test suite inside the image build. An image cannot be produced from a commit whose tests fail.

## Run the container

Development defaults need no credentials: mock attraction provider, AI disabled, port 8080.

```bash
docker run --rm -p 8080:8080 ai-travel-companion-api:local
```

Stop it with `Ctrl+C` in the foreground terminal, or from another terminal:

```bash
docker stop <container-id>   # sends SIGTERM, then SIGKILL after 10s by default
```

The app shuts down gracefully: in-flight requests finish, with `spring.lifecycle.timeout-per-shutdown-phase: 25s` as the upper bound. Keep this below the container stop timeout (ECS default 30s).

## Configuration (environment variables)

These names are read from `src/main/resources/application.yml`. Nothing else is read.

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port inside the container |
| `ATTRACTION_PROVIDER` | `mock` | `mock` or `google` |
| `GOOGLE_PLACES_API_KEY` | empty | Required only when `ATTRACTION_PROVIDER=google` |
| `TRAVEL_AI_ENABLED` | `false` | Enables AI personalization |
| `SPRING_AI_MODEL_CHAT` | `none` | Set to `openai` only with AI enabled |
| `OPENAI_API_KEY` | empty | Required only when `TRAVEL_AI_ENABLED=true` |
| `TRAVEL_AI_MODEL` | `gpt-4o-mini` | OpenAI chat model |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins for `/api/v1/**` |
| `RECOMMENDATION_SEARCH_RADIUS_KM` | `5` | Search radius |
| `RECOMMENDATION_WALKING_SPEED_KMH` | `4.5` | Walking speed |
| `RECOMMENDATION_MAX_RESULTS` | `3` | Max recommendations |
| `TRAVEL_SESSION_MIN_MOVEMENT_METERS` | `250` | Movement needed before a refresh |
| `TRAVEL_SESSION_MIN_REFRESH_INTERVAL_MINUTES` | `5` | Minimum time between refreshes |
| `TRAVEL_SESSION_INACTIVITY_TIMEOUT_MINUTES` | `30` | Idle timeout |
| `TRAVEL_SESSION_MAX_LIFETIME_MINUTES` | `120` | Absolute session lifetime |
| `TRAVEL_SESSION_RETENTION_MINUTES` | `30` | How long expired sessions answer 410/409 |
| `TRAVEL_SESSION_MAX_ACTIVE` | `100` | Concurrent active session limit |
| `TRAVEL_SESSION_CLEANUP_INTERVAL_MS` | `60000` | Expiry cleanup interval |

Pass values with `-e`, for example:

```bash
docker run --rm -p 8080:8080 -e SERVER_PORT=9090 -p 9090:9090 ai-travel-companion-api:local
```

Secrets must be passed at run time (`-e` or `--env-file`), never baked into the image. `.dockerignore` excludes `.env*`, `*.pem`, and `*.key`.

### Use the real Google Places provider later

```bash
docker run --rm -p 8080:8080 \
  -e ATTRACTION_PROVIDER=google \
  -e GOOGLE_PLACES_API_KEY="<your-key>" \
  ai-travel-companion-api:local
```

### Enable Spring AI later

```bash
docker run --rm -p 8080:8080 \
  -e TRAVEL_AI_ENABLED=true \
  -e SPRING_AI_MODEL_CHAT=openai \
  -e OPENAI_API_KEY="<your-key>" \
  ai-travel-companion-api:local
```

Without `OPENAI_API_KEY`, the app refuses to start when AI is enabled. The error message names the variable.

## Health check

- Endpoint: `GET /actuator/health`. It returns HTTP 200 with `{"status":"UP"}` when the app can accept requests.
- Only `health` is exposed over HTTP. Other actuator endpoints such as `/actuator/env` return 404. Details are hidden.
- The image defines a Docker `HEALTHCHECK` that calls this endpoint using busybox `wget`. This applies only to Docker. ECS and the ALB use their own health-check configuration.
- **Future ALB health-check path: `/actuator/health`**, expecting HTTP 200.

Verify it:

```bash
curl -i http://localhost:8080/actuator/health
```

## Example API calls

Mock mode returns Rome attractions, so the coordinates below are near the Colosseum.

```bash
curl -s -X POST http://localhost:8080/api/v1/recommendations \
  -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":41.8902,"longitude":12.4922},"interests":["HISTORY","FOOD"],"maxBudget":50.00,"currencyCode":"EUR","availableMinutes":180}'

# Create a session (returns 201 and sessionId)
curl -s -X POST http://localhost:8080/api/v1/travel-sessions \
  -H 'Content-Type: application/json' \
  -d '{"interests":["HISTORY","ART"],"maxBudget":50,"currencyCode":"EUR","availableMinutes":180}'

# Refresh recommendations for the session (replace <sessionId>)
curl -s -X POST http://localhost:8080/api/v1/travel-sessions/<sessionId>/recommendations \
  -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":41.9028,"longitude":12.4964}}'

# Stop the session (returns 204)
curl -i -X DELETE http://localhost:8080/api/v1/travel-sessions/<sessionId>
```

## Run Maven tests

```bash
./mvnw clean verify          # macOS/Linux
mvnw.cmd clean verify        # Windows
```

Surefire reports are written to `target/surefire-reports/`.

## GitHub Actions: backend-ci.yml

File: `.github/workflows/backend-ci.yml`

**Triggers:** pull requests targeting `master`, pushes to `master`, and manual `workflow_dispatch`.

**Permissions:** `contents: read` at the top level. Only the `ecr-push` job adds `id-token: write`, to get a short-lived OIDC token. No AWS access keys or Docker Hub credentials are stored. Images are pushed to ECR only on `master` pushes, after the Trivy gate passes.

**ECR job:** `ecr-push` runs after `docker-build` on `master` pushes only. It loads the scanned image artifact (the same bytes that were scanned), assumes the IAM role `ai-travel-companion-ecr-push` (region us-east-2) through OIDC, and pushes it to `512383926725.dkr.ecr.us-east-2.amazonaws.com/ai-travel-companion-api:<commit-sha>`. It skips the push if the tag already exists, because ECR tags are immutable. The role trusts only `repo:kodegravity/travel-api:ref:refs/heads/master` and can push only to this repository.

**Job 1: `build-and-test`** (`ubuntu-24.04`)
1. Checks out the repo without persisting credentials.
2. Sets up Temurin JDK 21 with Maven caching.
3. Runs `./mvnw -B -ntp clean verify`. The job fails if any test fails.
4. Writes a test count summary to the job summary.
5. Uploads `target/surefire-reports/` as the `surefire-test-reports` artifact, kept 14 days.

**Job 2: `docker-build`** (runs only after Job 1 succeeds)
1. Builds the image with Buildx, `push: false`, tagged `ai-travel-companion-api:<commit-sha>`. The commit SHA is also set as the `org.opencontainers.image.revision` label.
2. Checks that the image runs as UID/GID `10001:10001`.
3. Starts the container and waits up to 60 seconds for `/actuator/health` to return `UP`.
4. Scans the image with Trivy twice:
   - **Report:** all severities, as a table, never blocks.
   - **Gate:** blocks on `CRITICAL` or `HIGH` vulnerabilities that have a published fix. Unfixed findings and `MEDIUM`/`LOW` are reported but do not block.

Action references are pinned to full commit SHAs with the version as a comment.

### Inspect a failed build

1. Open the run in the **Actions** tab and check which job failed.
2. For test failures, read the job summary, then download the `surefire-test-reports` artifact and open the `TEST-*.xml` or `*.txt` files.
3. For Docker failures, expand the failing step. The smoke-test step prints `docker logs` on failure.
4. For vulnerability failures, open the **Vulnerability gate** step. It lists the package, installed version, and fixed version.

To reproduce locally:

```bash
./mvnw -B -ntp clean verify
docker build -t ai-travel-companion-api:local .
```

## Future AWS ECR and ECS plan (not implemented)

Planned pipeline after Chunk 1:

```
Push → build-and-test → docker-build (+ Trivy gate) → Amazon ECR → ECS Fargate service → ALB (/actuator/health) → deployment verification
```

Chunk 2 (implemented): OIDC auth and ECR push. Remaining integration points:
- **Authentication:** GitHub Actions OIDC with an IAM role assumed through `aws-actions/configure-aws-credentials`. No long-lived AWS access keys.
- **Registry:** push the scanned image to ECR. Tag with the commit SHA. Scan results are reviewed before the push.
- **ECS task definition:** non-root `10001:10001`, port 8080, env variables as listed above, secrets from AWS Secrets Manager or SSM Parameter Store (not plain environment values).
- **Stop timeout:** the service stop timeout must be greater than the 25s shutdown phase.
- **ALB target group:** health check path `/actuator/health`, success code 200.

## Known limitations

- Session state is in memory. A restart or redeploy loses active travel sessions.
- The Docker `HEALTHCHECK` is only used by Docker, not ECS.
- Trivy scans run against the image built in CI. The gate uses Trivy's vulnerability database, which may change between runs.

## Dependency versions and CVE overrides

- The backend runs on Spring Boot 4.0.8 (Spring Framework 7, Spring AI 2.0.1, Jackson 3 for `tools.jackson.*`). Test slices use `spring-boot-webmvc-test` and `spring-boot-resttestclient`.
- `pom.xml` pins `tomcat.version=11.0.25`, `jackson-bom.version=3.1.7`, and `jackson-2-bom.version=2.21.7` to clear Trivy findings that Boot 4.0.8 still manages at older versions. Remove these overrides once Boot ships the fixed versions.
- The runtime base image is pinned to `eclipse-temurin:21-jre-alpine-3.23` by digest. Its libexpat (2.8.5-r0) fixes CVE-2026-93990.
- Last local Trivy scan of the image (`--severity CRITICAL,HIGH`): 0 findings.
