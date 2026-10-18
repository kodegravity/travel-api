# ROAM / AI – AI Travel Companion (Backend)

A location-aware travel assistant. A traveler shares their location, interests, budget, and available time, and gets up to three nearby attractions they can realistically fit in, refreshed automatically as they move (Active Travel Mode).

The backend is three independent Spring Boot services. Each has its own `pom.xml`, Maven wrapper, Dockerfile, and image.

| Service | Port | Endpoints | Role |
|---|---|---|---|
| `services/travel-session-service` | 8080 | `/api/v1/recommendations`, `/api/v1/travel-sessions/**` (public) | Okta-protected public API, Active Travel Mode sessions, CORS |
| `services/recommendation-service` | 8081 | `/internal/v1/recommendations` | Eligibility filters, deterministic ranking, optional OpenAI selection |
| `services/places-discovery-service` | 8082 | `/internal/v1/places/search` | Attraction discovery: mock (Rome) or Google Places (New) |

```
Browser (Okta PKCE) -> travel-session -> recommendation -> places-discovery -> Google Places
                                              \-> OpenAI (optional)
```

Calls between services use `RestClient` and forward the caller's validated Okta token and `X-Request-Id`.

## Tech Stack

Java 21, Spring Boot 4.0.8, Spring Security OAuth2 resource server (Okta), Spring AI 2.0.1 (OpenAI), Maven wrapper, JUnit 5. Containers run as non-root on Eclipse Temurin 21 Alpine.

## Build, Test, Run

From any service directory:

```bash
./mvnw clean verify      # build and run the tests (no paid API calls)
OKTA_ISSUER=https://<domain>.okta.com/oauth2/default OKTA_AUDIENCE=api://default ./mvnw spring-boot:run
```

`OKTA_ISSUER` and `OKTA_AUDIENCE` are required by every service. Start places-discovery, then recommendation, then travel-session. Default base URLs point at `localhost:8082` and `localhost:8081`. See [docs/OKTA_LOCAL_SETUP.md](docs/OKTA_LOCAL_SETUP.md) and [docs/POSTMAN_TESTING.md](docs/POSTMAN_TESTING.md).

## Configuration

| Setting | Service | Env var | Default |
|---|---|---|---|
| Okta issuer / audience | all | `OKTA_ISSUER`, `OKTA_AUDIENCE` | required |
| Required scope | all | `TRAVEL_REQUIRED_SCOPE` | unset (any valid token) |
| Allowed browser origins | travel-session | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |
| Recommendation service URL | travel-session | `RECOMMENDATION_SERVICE_BASE_URL` | `http://localhost:8081` |
| Places service URL | recommendation | `PLACES_DISCOVERY_BASE_URL` | `http://localhost:8082` |
| Search radius / walking speed / max results | recommendation | `RECOMMENDATION_SEARCH_RADIUS_KM`, `RECOMMENDATION_WALKING_SPEED_KMH`, `RECOMMENDATION_MAX_RESULTS` | `5`, `4.5`, `3` |
| AI selection on/off | recommendation | `TRAVEL_AI_ENABLED` (needs `OPENAI_API_KEY`) | `false` |
| AI model | recommendation | `TRAVEL_AI_MODEL` | `gpt-4o-mini` |
| Session store | travel-session | `TRAVEL_SESSION_STORE` (`memory` or `dynamodb`), `TRAVEL_SESSION_TABLE` | `memory`, `roam-travel-sessions` |
| Attraction provider | places-discovery | `ATTRACTION_PROVIDER` (`mock` or `google`, needs `GOOGLE_PLACES_API_KEY`) | `mock` |

Secrets are supplied through environment variables (Secrets Manager on ECS) and are never logged or committed.

## Cost Controls

Paid APIs are off by default. When enabled, each one has a hard daily cap per running task, counted per UTC day:

| Control | Env var | Default | When exceeded |
|---|---|---|---|
| OpenAI calls per day | `TRAVEL_AI_MAX_CALLS_PER_DAY` | `300` | Falls back to deterministic ranking (users still get results) |
| OpenAI output tokens per call | `TRAVEL_AI_MAX_TOKENS` | `500` | Response is cut off at the cap |
| Google Places calls per day | `GOOGLE_PLACES_MAX_CALLS_PER_DAY` | `190` | Search returns 503 |

Budget: $100/month total (AWS + Google + OpenAI), at most $30/month each for Google and OpenAI. 190 Google calls/day stays within the 5,000 free monthly calls plus about $28 ($32 per 1,000 after that). 300 OpenAI calls/day with gpt-4o-mini is about $5/month at most.

The caps are per process and reset when a task restarts, so with N tasks the effective limit is N times the value. Provider-side limits are the hard guarantee. Also set provider-side limits: a daily quota on the Places API in Google Cloud, plus a budget alert, and a prepaid balance with auto-recharge off in OpenAI.

The AI engine also falls back to ranking when OpenAI errors or selects nothing usable, so an AI outage never hides eligible attractions.

## Health

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Only `health` is exposed. Other actuator endpoints return 404 by design.

## Docker, CI, and AWS

- Docker images and the GitHub Actions pipeline (build, test, Trivy scan, ECR push via OIDC): [docs/DOCKER_AND_CI.md](docs/DOCKER_AND_CI.md)
- Public entry point: API Gateway HTTP API (HTTPS, Okta JWT authorizer, 5 req/s throttle) -> VPC link -> travel-session. recommendation and places-discovery are reachable only over Service Connect. Terraform: `infra/terraform/api-gateway`
- ECS Fargate deployment (cluster `ai-travel-app`, us-east-2): Terraform in `infra/terraform/ecs-services`; `infra/ecs/*.json` are reference templates. Session table: `infra/terraform/session-store`. See also [docs/ECS_DEPLOYMENT.md](docs/ECS_DEPLOYMENT.md)
- Logs: CloudWatch `/ecs/ai-travel-companion-<service>`, forwarded to Datadog by the Datadog Forwarder Lambda
- Frontend integration: [docs/FRONTEND_API_INTEGRATION.md](docs/FRONTEND_API_INTEGRATION.md)

## API: Recommendations

`POST /api/v1/recommendations` (travel-session-service, `Content-Type: application/json`, `Authorization: Bearer <Okta access token>`)

Eligibility: within the search radius (5 km, Haversine); at least one selected interest; known duration; walking time plus visit duration within `availableMinutes`; cost within `maxBudget` only when the currency matches the request. Attractions with an unknown cost are kept with a neutral budget score, and currency mismatches are not converted. Ranking uses interest 40%, distance 25%, budget 20%, time 15% (see `RecommendationRankingService`), with ties broken by attraction ID. At most 3 results are returned. Recommendation reasons are generated deterministically from known data.

### Request

```json
{
  "location": { "latitude": 48.8584, "longitude": 2.2945 },
  "interests": ["HISTORY", "FOOD"],
  "maxBudget": 50.00,
  "currencyCode": "EUR",
  "availableMinutes": 120
}
```

| Field | Rules |
|---|---|
| `location.latitude` | required, -90 to 90 |
| `location.longitude` | required, -180 to 180 |
| `interests` | required, at least one of `HISTORY`, `FOOD`, `ART`, `NATURE`, `SHOPPING`, `LOCAL_EXPERIENCES` |
| `maxBudget` | required, >= 0 |
| `currencyCode` | required, ISO 4217 code (e.g. `EUR`, `USD`) |
| `availableMinutes` | required, 15 to 720 |

### Responses

- **200 OK** (valid request): `{"recommendations": [...]}`. An empty list means no attraction qualified.
- **400 Bad Request** (validation failure): `title: "Validation failed"` with an `errors` map of field to message, for example:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed. See 'errors' for field-level details.",
  "instance": "/api/v1/recommendations",
  "errors": { "location.latitude": "must be between -90 and 90" }
}
```

- **400 Bad Request** (malformed JSON or unknown enum value): `title: "Malformed request"`. Stack traces and parser internals are never returned.

Example successful response for a traveler at the Colosseum (`41.8902, 12.4922`, `HISTORY`, `50 EUR`, `180` minutes). The response has three results, the cap set by `RECOMMENDATION_MAX_RESULTS`. The first item is shown:

```json
{
  "recommendations": [
    {
      "placeId": "pantheon",
      "name": "Pantheon",
      "category": "HISTORY, ART",
      "description": "Matches your interests: HISTORY. About 1.6 km away (about 21 min walk). Estimated cost EUR 5.00 is within your budget of EUR 50.00. Estimated visit about 45 min within your 180 available minutes.",
      "latitude": 41.8986,
      "longitude": 12.4769,
      "estimatedDurationMinutes": 45,
      "estimatedCost": 5.0,
      "currencyCode": "EUR",
      "mapsUrl": "https://www.google.com/maps/dir/?api=1&origin=41.8902,12.4922&destination=41.8986,12.4769&travelmode=walking"
    }
  ]
}
```

Results are ordered by score. Ties are broken by attraction ID.

### Example curl requests

```bash
# Valid request from Rome -> 200
curl -i -X POST http://localhost:8080/api/v1/recommendations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":41.8902,"longitude":12.4922},"interests":["HISTORY"],"maxBudget":50.00,"currencyCode":"EUR","availableMinutes":180}'

# Invalid latitude -> 400
curl -i -X POST http://localhost:8080/api/v1/recommendations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":91,"longitude":2.2945},"interests":["ART"],"maxBudget":10,"currencyCode":"USD","availableMinutes":60}'

# Malformed JSON -> 400
curl -i -X POST http://localhost:8080/api/v1/recommendations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"location":'
```

## Git Notes

`target/`, IDE files (`.idea/`, `*.iml`), and local secret files (`.env`, `application-local.yml`) are ignored via `.gitignore`.

## Google Places provider

The attraction source is selected with `ATTRACTION_PROVIDER`:

| Value | Behaviour |
|---|---|
| `mock` (default) | In-memory Rome development data. No network calls. |
| `google` | Calls Google Places API (New) `places:searchNearby`. Requires `GOOGLE_PLACES_API_KEY`. |

Run places-discovery against Google:

```bash
export GOOGLE_PLACES_API_KEY=your-key-here   # never commit this value
export ATTRACTION_PROVIDER=google
./mvnw spring-boot:run
```

If `ATTRACTION_PROVIDER=google` and the key is missing, startup fails with a message naming `GOOGLE_PLACES_API_KEY`. The key is never logged or returned in responses.

Notes:
- Requests use the `X-Goog-FieldMask` header `places.id,places.displayName,places.location,places.types`. Google bills by field, so add fields only when needed.
- Google does not return visit duration, cost, or currency. Durations are documented per-category estimates (HISTORY/ART 90 min; NATURE/FOOD/SHOPPING/LOCAL_EXPERIENCES 60 min). Costs and currency stay `null`.
- If Google is unavailable or returns an error, the API responds with HTTP 503 (RFC 9457 ProblemDetail) and a generic message.

Tests: `./mvnw clean verify` uses the mock provider and mocks Google HTTP with `MockRestServiceServer`. No real key is needed.

## Active Travel Mode

A traveler starts a short-lived session with preferences, then sends their location whenever the web page has permission to share it. The backend does not track the device.

### Session lifecycle

1. `POST /api/v1/travel-sessions` creates an `ACTIVE` session and returns a random UUID.
2. `POST /api/v1/travel-sessions/{sessionId}/recommendations` sends a location. The first call always generates recommendations.
3. Later calls refresh only when the traveler has moved and enough time has passed (see rules below).
4. `DELETE /api/v1/travel-sessions/{sessionId}` stops the session and clears preferences, location, and recommended IDs.

Sessions belong to the authenticated user. Another user's session ID returns 404.

### Endpoints

#### Create session

```bash
curl -s -X POST localhost:8080/api/v1/travel-sessions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"interests":["HISTORY","ART"],"maxBudget":50,"currencyCode":"EUR","availableMinutes":180}'
```

`201 Created` (`Location` header set):

```json
{ "sessionId": "b0f6ea0e-f9bc-4584-94fe-cf0dcb8a96f4", "status": "ACTIVE", "expiresAt": "2026-10-16T04:22:06Z" }
```

Validation rules match `POST /api/v1/recommendations` (interests, budget, ISO-4217 currency, available minutes). No location is collected at this step.

#### Refresh recommendations

```bash
curl -s -X POST localhost:8080/api/v1/travel-sessions/$SESSION_ID/recommendations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":41.9028,"longitude":12.4964}}'
```

`200 OK`:

```json
{
  "sessionId": "b0f6ea0e-f9bc-4584-94fe-cf0dcb8a96f4",
  "refreshStatus": "UPDATED",
  "recommendations": [ { "placeId": "pantheon", "name": "Pantheon", "...": "..." } ],
  "generatedAt": "2026-10-16T03:52:06Z"
}
```

| `refreshStatus` | Meaning | `recommendations` |
|---|---|---|
| `UPDATED` | Providers were called and up to three unseen eligible attractions were selected. | Up to 3 items |
| `UNCHANGED` | Refresh rules were not met. No provider or LLM call was made. | `[]` |
| `NO_NEW_RESULTS` | A successful evaluation found no unseen eligible attractions. | `[]` |

#### Stop session

```bash
curl -i -X DELETE localhost:8080/api/v1/travel-sessions/$SESSION_ID -H "Authorization: Bearer $TOKEN"
```

`204 No Content`. Repeating the request on a stopped session also returns `204`, so the call is idempotent.

### Session rules

| Rule | Default | Property |
|---|---|---|
| Inactivity expiry (extended on each valid interaction) | 30 min | `travel.session.inactivity-timeout-minutes` |
| Absolute lifetime cap | 120 min | `travel.session.max-lifetime-minutes` |
| Minimum movement for an automatic refresh | 250 m | `travel.session.minimum-movement-meters` |
| Minimum time between evaluations | 5 min | `travel.session.minimum-refresh-interval-minutes` |
| Maximum recommendations per refresh | 3 (`recommendation.max-recommendations`) | |
| Maximum concurrently active sessions | 100 | `travel.session.max-active-sessions` |
| Expired-session retention before purge | 30 min | `travel.session.retention-after-expiry-minutes` |

Refresh runs only when both movement ≥ 250 m **and** elapsed time ≥ 5 min since the last successful evaluation. A skipped refresh does not move the baseline. Provider failures do not count as an evaluation.

### Duplicate prevention

Each session stores the IDs of attractions it has already recommended. Those IDs are excluded before ranking and the top-three cut, so the pipeline can select the next eligible candidates. When every eligible attraction has been shown, the response is `NO_NEW_RESULTS`. Public `POST /api/v1/recommendations` behaviour is unchanged.

### Error responses

All errors use RFC 9457 `application/problem+json`.

| Case | Status |
|---|---|
| Missing, expired, or invalid Okta token | 401 |
| Token lacks the required scope | 403 |
| Invalid JSON body, validation failure, invalid location | 400 |
| Malformed session UUID | 400 |
| Unknown session (or expired and purged) | 404 |
| Expired session within retention | 410 |
| Stopped session on refresh | 409 |
| Refresh already in progress for this session | 409 |
| Session changed by another request at the same moment (retry) | 409 |
| Session capacity reached | 503 |
| Recommendation provider unavailable | 503 |

Responses never include stack traces, API keys, or coordinates.

### Session store

| `TRAVEL_SESSION_STORE` | Behaviour |
|---|---|
| `memory` (default) | Process-local. Fine for local development and a single task. Sessions are lost on restart. |
| `dynamodb` | Table `TRAVEL_SESSION_TABLE` (partition key `sessionId`, on-demand, TTL on `purgeAt`). Sessions survive restarts and any task can serve any request. |

DynamoDB writes are conditional on a `version` attribute, so a stale copy never overwrites a newer change such as a stop. The one-refresh-at-a-time rule is enforced across tasks with a lease (`TRAVEL_SESSION_REFRESH_LEASE_SECONDS`, default 30) that expires on its own if a task dies mid-refresh. Stopped and expired sessions keep no preferences or location, and DynamoDB TTL deletes them after the retention window.

The table and the task's IAM role are defined in `infra/terraform/session-store`. The integration tests run against DynamoDB Local:

```bash
docker run -d --rm -p 8000:8000 amazon/dynamodb-local:2.6.1 -jar DynamoDBLocal.jar -inMemory
DYNAMODB_TEST_ENDPOINT=http://localhost:8000 ./mvnw test   # in services/travel-session-service
```

Without `DYNAMODB_TEST_ENDPOINT` those tests are skipped. CI runs them with a DynamoDB Local service container.

### Concurrency and expiry

- Only one refresh per session can run at a time, across all tasks. A second concurrent refresh gets `409`.
- Provider calls run without holding a lock.
- A stop or expiry that happens during a refresh discards the refresh result.
- A scheduled job (`travel.session.cleanup-interval-ms`, default 60 s) removes expired sessions past retention.

### Testing

```bash
./mvnw clean verify
```

Tests use a controllable `Clock` (`support/MutableClock`) and mock the recommendation service. They make no paid Google Places or OpenAI calls.

### Limitations

- With the default `memory` store, restarting ends all sessions and sessions are not shared between tasks. Use `TRAVEL_SESSION_STORE=dynamodb` for deployments (see Session store).
- The DynamoDB capacity check is not atomic across tasks, so simultaneous creates can exceed `max-active-sessions` by a few.
- Every endpoint requires an Okta access token, and a session is visible only to the user (`sub`) who created it.
- Expired tombstones are retained until the retention period ends. Total memory is bounded by creation rate, not by active session count alone.
- When `TRAVEL_AI_ENABLED=true`, refreshes also go through the AI engine, so each `UPDATED` refresh can cost one Google call and one OpenAI call. `UNCHANGED` refreshes cost nothing.

### Integration with a React frontend

1. Create a session once and store `sessionId` in memory (not `localStorage`).
2. While the page is visible and permission is granted, call `navigator.geolocation.watchPosition` and POST the location to the refresh endpoint. Throttle client-side to avoid unnecessary calls.
3. On `404` or `410`, create a new session.
4. Call `DELETE` when the traveler leaves the active mode.
5. Handle `UNCHANGED`, `NO_NEW_RESULTS`, and `UPDATED` without retry loops. Retry only `503`, with backoff.
