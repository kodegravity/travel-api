# AI Travel Companion – Backend API

A location-aware travel assistant API that will help international travelers discover nearby attractions and activities based on location, interests, available time, and budget.

This repository currently contains only the initial application skeleton (Task 1 of the backlog): Spring Boot setup, configuration, and a health check. Business features will be added in later stories.

## Tech Stack

- Java 21
- Spring Boot 4.0.8 (Spring Framework 7, Spring AI 2.0.1)
- Maven (with Maven Wrapper)
- Spring Web MVC, Spring Boot Validation, Spring Boot Actuator
- JUnit 5 and Spring Boot Test
- Embedded Tomcat

## Prerequisites

- JDK 21 (any vendor). Verify with `java -version`.
- No Maven installation is required; the included wrapper (`mvnw`) downloads Maven 3.9.9 on first use.

## Project Structure

```
travel-companion-api/
├── .mvn/wrapper/maven-wrapper.properties
├── mvnw / mvnw.cmd
├── pom.xml
└── src
    ├── main
    │   ├── java/com/aitravel/companion
    │   │   ├── TravelCompanionApplication.java   # Spring Boot entry point
    │   │   ├── config/        # Spring configuration classes (future)
    │   │   ├── controller/    # REST controllers (future)
    │   │   ├── dto/           # Request/response objects (future)
    │   │   ├── exception/     # Error handling (future)
    │   │   ├── model/         # Domain model (future)
    │   │   ├── provider/      # External providers, e.g. Places/AI (future)
    │   │   ├── repository/    # Persistence (future)
    │   │   └── service/       # Business logic (future)
    │   └── resources
    │       └── application.yml
    └── test
        └── java/com/aitravel/companion
            └── TravelCompanionApplicationTests.java
```

The empty packages contain `.gitkeep` placeholders so Git tracks them.

## Configuration

Configuration lives in `src/main/resources/application.yml`.

| Setting | Default | Environment variable |
|---|---|---|
| Server port | `8080` | `SERVER_PORT` |
| Search radius (km) | `5` | `RECOMMENDATION_SEARCH_RADIUS_KM` |
| Walking speed (km/h) | `4.5` | `RECOMMENDATION_WALKING_SPEED_KMH` |
| Max recommendations | `3` | `RECOMMENDATION_MAX_RESULTS` |

Actuator exposes only `health`, with details hidden. Secrets and API keys must never be committed. Future secrets will be supplied through environment variables.

## Docker and CI

The container image, environment variables, health check, and GitHub Actions pipeline are documented in [docs/DOCKER_AND_CI.md](docs/DOCKER_AND_CI.md).

## Build and Test

```bash
./mvnw clean verify          # macOS/Linux
mvnw.cmd clean verify        # Windows
```

## Run Locally

```bash
./mvnw spring-boot:run

# or build and run the JAR
./mvnw clean package
java -jar target/travel-companion-api-0.0.1-SNAPSHOT.jar

# optional: run on another port
SERVER_PORT=9090 ./mvnw spring-boot:run
```

## Verify the Health Endpoint

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Other actuator endpoints (for example `/actuator/env`) return 404 by design.

## API: Recommendations (ATC-007, Chunk 3)

`POST /api/v1/recommendations` (`Content-Type: application/json`)

Valid requests return **HTTP 200** with recommendations from the `MockAttractionProvider` (10 mock attractions in Rome, EUR). Costs and durations in the mock data are development values, not verified prices or opening hours.

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
  -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":41.8902,"longitude":12.4922},"interests":["HISTORY"],"maxBudget":50.00,"currencyCode":"EUR","availableMinutes":180}'

# Invalid latitude -> 400
curl -i -X POST http://localhost:8080/api/v1/recommendations \
  -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":91,"longitude":2.2945},"interests":["ART"],"maxBudget":10,"currencyCode":"USD","availableMinutes":60}'

# Malformed JSON -> 400
curl -i -X POST http://localhost:8080/api/v1/recommendations \
  -H 'Content-Type: application/json' -d '{"location":'
```

## Git Notes

`target/`, IDE files (`.idea/`, `*.iml`), and local secret files (`.env`, `application-local.yml`) are ignored via `.gitignore`.

## Google Places provider (Chunk 4)

The attraction source is selected with `ATTRACTION_PROVIDER`:

| Value | Behaviour |
|---|---|
| `mock` (default) | In-memory Rome development data. No network calls. |
| `google` | Calls Google Places API (New) `places:searchNearby`. Requires `GOOGLE_PLACES_API_KEY`. |

Run against Google:

```bash
export GOOGLE_PLACES_API_KEY=your-key-here   # never commit this value
export ATTRACTION_PROVIDER=google
./mvnw spring-boot:run
```

If `ATTRACTION_PROVIDER=google` and the key is missing, startup fails with a message naming `GOOGLE_PLACES_API_KEY`. The key is never logged or returned in responses.

Notes:
- Requests use the `X-Goog-FieldMask` header `places.id,places.displayName,places.location,places.types,places.googleMapsUri`. Google bills by field, so add fields only when needed.
- Google does not return visit duration, cost, or currency. Durations are documented per-category estimates (HISTORY/ART 90 min; NATURE/FOOD/SHOPPING/LOCAL_EXPERIENCES 60 min). Costs and currency stay `null`.
- If Google is unavailable or returns an error, the API responds with HTTP 503 (RFC 9457 ProblemDetail) and a generic message.

Tests: `./mvnw clean verify` uses the mock provider and mocks Google HTTP with `MockRestServiceServer`. No real key is needed.

## Active Travel Mode (Chunk 6)

A traveler starts a short-lived session with preferences, then sends their location whenever the web page has permission to share it. The backend does not track the device. Session state lives only in memory, and restarting the application removes all active sessions.

### Session lifecycle

1. `POST /api/v1/travel-sessions` creates an `ACTIVE` session and returns a random UUID.
2. `POST /api/v1/travel-sessions/{sessionId}/recommendations` sends a location. The first call always generates recommendations.
3. Later calls refresh only when the traveler has moved and enough time has passed (see rules below).
4. `DELETE /api/v1/travel-sessions/{sessionId}` stops the session and clears preferences, location, and recommended IDs.

The session ID is a bearer credential. Anyone holding it can use the session, so treat it as a secret and send it only over HTTPS.

### Endpoints

#### Create session

```bash
curl -s -X POST localhost:8080/api/v1/travel-sessions \
  -H 'Content-Type: application/json' \
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
  -H 'Content-Type: application/json' \
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
curl -i -X DELETE localhost:8080/api/v1/travel-sessions/$SESSION_ID
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
| Invalid JSON body, validation failure, invalid location | 400 |
| Malformed session UUID | 400 |
| Unknown session (or expired and purged) | 404 |
| Expired session within retention | 410 |
| Stopped session on refresh | 409 |
| Refresh already in progress for this session | 409 |
| Session capacity reached | 503 |
| Recommendation provider unavailable | 503 |

Responses never include stack traces, API keys, or coordinates.

### Concurrency and expiry

- Only one refresh per session can run at a time. A second concurrent refresh gets `409`.
- Provider calls run without holding a lock.
- A stop or expiry that happens during a refresh discards the refresh result.
- A scheduled job (`travel.session.cleanup-interval-ms`, default 60 s) removes expired sessions past retention.

### Testing

```bash
./mvnw clean verify
```

Tests use a controllable `Clock` (`support/MutableClock`) and mock the recommendation service. They make no paid Google Places or OpenAI calls.

### Limitations

- Sessions are in memory only. Restarting the application ends all sessions. Use a shared store (for example Redis) before running multiple instances.
- Session IDs are bearer tokens with no authentication layer yet.
- Expired tombstones are retained until the retention period ends. Total memory is bounded by creation rate, not by active session count alone.
- AI personalization (Chunk 5) is not wired into the refresh path. Refresh uses the deterministic recommendation pipeline only.

### Integration with a React frontend

1. Create a session once and store `sessionId` in memory (not `localStorage`).
2. While the page is visible and permission is granted, call `navigator.geolocation.watchPosition` and POST the location to the refresh endpoint. Throttle client-side to avoid unnecessary calls.
3. On `404` or `410`, create a new session.
4. Call `DELETE` when the traveler leaves the active mode.
5. Handle `UNCHANGED`, `NO_NEW_RESULTS`, and `UPDATED` without retry loops. Retry only `503`, with backoff.
