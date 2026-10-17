# Microservice Migration

The original Spring Boot application (port 8080) stays functional during the migration. Services are extracted one at a time, and the old implementations are removed only after the new service is verified.

## Migration inventory

| Existing class | Target service | Status |
|---|---|---|
| `AttractionProvider`, `GooglePlacesAttractionProvider`, `MockAttractionProvider`, `GooglePlacesApi`, `GooglePlaceMapper`, `GooglePlaceTypeMapping`, `GooglePlacesConfig`, `GooglePlacesProperties`, `Attraction` | Places Discovery (8082) | Copied; original still in main app |
| `DefaultRecommendationService`, `RecommendationService`, `RecommendationRankingService`, `DistanceCalculator`, `SpringAIRecommendationService`, `AIRecommendationService`, `RecommendationConfig`, `TravelAiProperties`, `AiConfig` | Recommendation (8081) | Copied; original still in main app |
| `RemotePlacesAttractionProvider`, `PlacesDiscoveryConfig` | Recommendation (8081) adapter to Places | Copied (unconditional, always remote) |
| `TravelSessionController`, `TravelSessionService`, `TravelSessionRepository`, `InMemoryTravelSessionRepository`, `TravelSession*`, session DTOs, `TravelSessionConfig`, `TravelSessionProperties`, CORS config | Travel Session (8080) | Copied; original still in main app |
| `RecommendationController` (public `/api/v1/recommendations`) | Travel Session (8080) proxy | New in session service |
| `RemoteRecommendationService` | Travel Session (8080) adapter to Recommendation | New |

The main application (`src/`) still contains all of the original code. It has not been changed to call the new services, and it has not been removed. Removal is a separate step after approval.

## Places Discovery Service (port 8082)

Location: `services/places-discovery-service`

### Internal contract (v1)

`POST /internal/v1/places/search`

Request:

```json
{
  "location": { "latitude": 41.9028, "longitude": 12.4964 },
  "searchRadiusKm": 5
}
```

- `latitude`: -90 to 90, required
- `longitude`: -180 to 180, required
- `searchRadiusKm`: 0.1 to 50, required (Google's circle limit is 50 km)

Response `200`:

```json
{ "attractions": [ { "id": "colosseum", "name": "Colosseum", "categories": ["HISTORY"], "latitude": 41.8902, "longitude": 12.4922, "estimatedCost": 16.00, "currencyCode": "EUR", "estimatedDurationMinutes": 120, "description": "..." } ] }
```

Empty results return `200` with `"attractions": []`.

Errors:

| Status | Cause |
|---|---|
| 400 | Validation failure or malformed body (`application/problem+json`, with `errors` for field-level details) |
| 503 | Provider failure (Google down or returned an error). Details are logged only. |
| 500 | Unexpected error |

### Configuration

| Property | Env var | Default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8082` |
| `attraction.provider` | `ATTRACTION_PROVIDER` | `mock` (`google` is also supported) |
| `google.places.api-key` | `GOOGLE_PLACES_API_KEY` | unset (required only for `google`) |
| `google.places.*` | `GOOGLE_PLACES_*` | see `application.yml` |

### Run it

```bash
./mvnw -q -f services/places-discovery-service/pom.xml package -DskipTests
java -jar services/places-discovery-service/target/places-discovery-service-0.0.1-SNAPSHOT.jar
curl -s localhost:8082/actuator/health
```

## Main application adapter

With `attraction.provider=remote`, `RemotePlacesAttractionProvider` replaces the in-process provider. It calls the places service with `RestClient`.

| Property | Env var | Default |
|---|---|---|
| `attraction.provider` | `ATTRACTION_PROVIDER` | `mock` (set to `remote` to use the service) |
| `places.discovery.base-url` | `PLACES_DISCOVERY_BASE_URL` | `http://localhost:8082` |
| `places.discovery.connect-timeout` | | `2s` |
| `places.discovery.read-timeout` | | `8s` (above the places service's 5s Google read timeout, so upstream errors come back as HTTP responses) |

Failure behavior: connection errors, timeouts, and non-2xx responses all become `AttractionProviderException`, which the main app maps to `503 Attraction provider unavailable`. The remote adapter never falls back to mock data.

The main app keeps its own copies of the request and response records. There is no shared library. Keep the contract in sync manually until the contract is versioned and the shared module is justified.

## Future extraction plan

Remaining work: remove the duplicated code from the main application after approval, then decide whether to retire the main application entirely.

## Recommendation Service (port 8081)

Location: `services/recommendation-service`. Package: `com.aitravel.recommendation`.

Contains the ranking pipeline (`DefaultRecommendationService`, `RecommendationRankingService`, `DistanceCalculator`), optional AI personalisation (`SpringAIRecommendationService`, enabled by `TRAVEL_AI_ENABLED=true`), and an HTTP adapter to Places Discovery.

### Internal contract (v1)

`POST /internal/v1/recommendations`

```json
{
  "request": { "location": { "latitude": 41.9028, "longitude": 12.4964 }, "interests": ["HISTORY"], "maxBudget": 50, "currencyCode": "EUR", "availableMinutes": 180 },
  "excludedPlaceIds": ["pantheon"]
}
```

Response `200`: `{ "recommendations": [ ... ] }`, the same shape as the public endpoint. Errors match the public API: `400` for validation, `503` when Places Discovery is unavailable.

### Configuration

| Property | Env var | Default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8081` |
| `places.discovery.base-url` | `PLACES_DISCOVERY_BASE_URL` | `http://localhost:8082` |
| `recommendation.*` | `RECOMMENDATION_*` | as in the root `application.yml` |
| `travel.ai.enabled` | `TRAVEL_AI_ENABLED` | `false` |

## Travel Session Service (port 8080)

Location: `services/travel-session-service`. Package: `com.aitravel.session`.

Owns session lifecycle and refresh rules. It does not rank attractions. Session refreshes call Recommendation Service through `RemoteRecommendationService`, which passes the session's already-recommended place IDs as `excludedPlaceIds`. Upstream failures become `503 Attraction provider unavailable`, the same as before the split.

| Property | Env var | Default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8080` |
| `recommendation-service.base-url` | `RECOMMENDATION_SERVICE_BASE_URL` | `http://localhost:8081` |
| `recommendation-service.connect-timeout` | | `2s` |
| `recommendation-service.read-timeout` | | `10s` |
| `travel.session.*` | `TRAVEL_SESSION_*` | as in the root `application.yml` |
| `travel.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |

### Run the three services

```bash
SERVER_PORT=8082 java -jar services/places-discovery-service/target/places-discovery-service-0.0.1-SNAPSHOT.jar
SERVER_PORT=8081 PLACES_DISCOVERY_BASE_URL=http://localhost:8082 java -jar services/recommendation-service/target/recommendation-service-0.0.1-SNAPSHOT.jar
SERVER_PORT=8080 RECOMMENDATION_SERVICE_BASE_URL=http://localhost:8081 java -jar services/travel-session-service/target/travel-session-service-0.0.1-SNAPSHOT.jar
```

### Public API preservation

The frontend API stays the same throughout:

- `POST /api/v1/recommendations`
- `POST /api/v1/travel-sessions`
- `POST /api/v1/travel-sessions/{sessionId}/recommendations`
- `DELETE /api/v1/travel-sessions/{sessionId}`

The standalone recommendation endpoint remains publicly reachable after extraction. Options:

- The Travel Session Service (or a gateway) on 8080 proxies `/api/v1/recommendations` to Recommendation Service on 8081.
- Or the frontend calls the gateway, which routes each path to its owning service.

The Travel Session Service serves these paths. It proxies `POST /api/v1/recommendations` to Recommendation Service, so the frontend URL is unchanged.

## Verification status

- Places Discovery: 28 tests passing.
- Recommendation Service: 48 tests passing, including the internal endpoint (valid, invalid coordinates, provider 503).
- Travel Session Service: 56 tests passing, including the HTTP client (success with exclusions, 5xx, timeout). Session integration tests use a deterministic stub for the Recommendation Service.
- Main application: 135 tests passing (unchanged).
- Local run (ports 18082, 18081, 18080, plus main app on 18090): health UP on all four; `POST /api/v1/recommendations` through the Session Service is byte-identical to the main app's in-process response; session create, refresh, and delete work.
- With Recommendation Service stopped, the Session Service returns `503 Attraction provider unavailable` with no fallback data.
