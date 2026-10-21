# AI Travel Companion – Frontend API Integration

This document describes the Spring Boot backend contract for the separately maintained React frontend. It reflects the implemented code. It does not describe planned features.

## Base URL and CORS

| Environment | Backend base URL | Allowed browser origin |
|---|---|---|
| Local development | `http://localhost:8080` | `http://localhost:5173` (default) |
| Production | Set by deployment | Set with `CORS_ALLOWED_ORIGINS` |

- CORS applies only to `/api/v1/**`. Actuator endpoints are not cross-origin accessible.
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`. Allowed request headers: `Content-Type`, `Accept`.
- Credentials are not allowed (`Access-Control-Allow-Credentials` is absent). The frontend must not send cookies.
- Preflight responses are cached for 30 minutes (`Access-Control-Max-Age: 1800`).
- A request from an origin that is not in the list receives no `Access-Control-Allow-Origin` header. A preflight from such an origin returns `403`.
- Wildcards (`*`) are rejected at startup.

Configure origins with a comma-separated list:

```bash
export CORS_ALLOWED_ORIGINS=http://localhost:5173,https://travel.example.com
```

## Endpoints

| Method | Path | Purpose | Success |
|---|---|---|---|
| POST | `/api/v1/recommendations` | Stateless one-off recommendations. | `200` |
| POST | `/api/v1/travel-sessions` | Create an active travel session. | `201` |
| POST | `/api/v1/travel-sessions/{sessionId}/recommendations` | Submit a location and refresh recommendations. | `200` |
| DELETE | `/api/v1/travel-sessions/{sessionId}` | Stop the session and clear its data. | `204` |
| GET | `/api/v1/travel-plan` | The signed-in traveler's synced plan (`version` 0 when empty). | `200` |
| PUT | `/api/v1/travel-plan` | Replace the plan if it is still at `baseVersion`. `409` if another device saved first, `413` over 350 KB. | `200` |

All request and response bodies are JSON (`Content-Type: application/json`).

## Enum values

**Interest** (`interests` fields): `HISTORY`, `FOOD`, `ART`, `NATURE`, `SHOPPING`, `LOCAL_EXPERIENCES`

**RefreshStatus**:

| Value | Meaning | `recommendations` |
|---|---|---|
| `UPDATED` | A successful evaluation found up to three unseen eligible attractions. | 1–3 items |
| `UNCHANGED` | The refresh rules were not met. No provider or AI call was made. | `[]` |
| `NO_NEW_RESULTS` | A successful evaluation found no unseen eligible attractions. | `[]` |

**TravelSessionStatus** (returned on creation): `ACTIVE`. Stopped and expired states are reported through errors.

## Request DTOs

### Location (`location` in refresh and recommendation requests)

| Field | Type | Required | Rules |
|---|---|---|---|
| `latitude` | number | yes | −90 to 90 |
| `longitude` | number | yes | −180 to 180 |

### CreateTravelSessionRequest

| Field | Type | Required | Rules |
|---|---|---|---|
| `interests` | array of Interest | yes | Non-empty. No null values. |
| `maxBudget` | number | yes | ≥ 0 |
| `currencyCode` | string | yes | ISO-4217 code, e.g. `EUR` |
| `availableMinutes` | integer | yes | 15 to 720 |

No location is collected at session creation.

### TravelSessionRefreshRequest

| Field | Type | Required |
|---|---|---|
| `location` | Location | yes |

### RecommendationRequest (stateless endpoint)

| Field | Type | Required | Rules |
|---|---|---|---|
| `location` | Location | yes | |
| `interests` | array of Interest | yes | Non-empty |
| `maxBudget` | number | yes | ≥ 0 |
| `currencyCode` | string | yes | ISO-4217 |
| `availableMinutes` | integer | yes | 15 to 720 |

## Response DTOs

### CreateTravelSessionResponse (`201`)

| Field | Type | Notes |
|---|---|---|
| `sessionId` | string (UUID) | Bearer credential. Store it in memory only. |
| `status` | string | Always `ACTIVE` on creation. |
| `expiresAt` | string (ISO-8601 instant) | 30 minutes after creation. Extended on activity, up to the lifetime cap. |

### TravelSessionRecommendationResponse (`200`)

| Field | Type | Notes |
|---|---|---|
| `sessionId` | string (UUID) | Echoes the session. |
| `refreshStatus` | RefreshStatus | See table above. |
| `recommendations` | array of RecommendationDto | Empty unless `UPDATED`. |
| `generatedAt` | string (ISO-8601 instant) | Server time of evaluation. |

### RecommendationResponse (`200`, stateless endpoint)

| Field | Type |
|---|---|
| `recommendations` | array of RecommendationDto |

### RecommendationDto

Optional fields are **omitted** from JSON when unknown. Treat a missing key as `null`.

| Field | Type | Always present | Notes |
|---|---|---|---|
| `placeId` | string | yes | Stable attraction ID. Used for duplicate suppression. |
| `name` | string | yes | |
| `category` | string | yes | Comma-separated interest labels, e.g. `HISTORY, ART`. |
| `description` | string | yes | Human-readable explanation. |
| `latitude` | number | yes | |
| `longitude` | number | yes | |
| `estimatedDurationMinutes` | integer | no | Category-based estimate, not a Google value. |
| `estimatedCost` | number | no | Omitted when cost is unknown. |
| `currencyCode` | string | no | Present only when `estimatedCost` is present. |
| `mapsUrl` | string | no | Google Maps walking directions from the traveler's location to the attraction. |

Example RecommendationDto:

```json
{
  "placeId": "pantheon",
  "name": "Pantheon",
  "category": "HISTORY, ART",
  "description": "Matches your interests: HISTORY, ART. About 1.7 km away (about 23 min walk). Estimated cost EUR 5.00 is within your budget of EUR 50.00. Estimated visit about 45 min within your 180 available minutes.",
  "latitude": 41.8986,
  "longitude": 12.4769,
  "estimatedDurationMinutes": 45,
  "estimatedCost": 5.00,
  "currencyCode": "EUR",
  "mapsUrl": "https://www.google.com/maps/dir/?api=1&origin=41.9028,12.4964&destination=41.8986,12.4769&travelmode=walking"
}
```

## Travel plan

The plan holds the traveler's trips, favorites, and itineraries so they follow them across devices. The backend stores it as given; the web app owns the shape of each entry's `data` and does the merging.

```json
{
  "ownerRef": "64 hex characters: SHA-256 of the Okta subject",
  "version": 3,
  "entries": {
    "city:madrid": { "updatedAt": "2026-10-20T10:00:00Z", "data": { "trip": [], "settings": null, "itinerary": {} } },
    "favorites":   { "updatedAt": "2026-10-20T09:00:00Z", "data": [] },
    "city:rome":   { "updatedAt": "2026-10-20T11:00:00Z", "data": null }
  }
}
```

- Keys are `favorites` or `city:<cityId>` (`[A-Za-z0-9_-]{1,64}`), at most 200 entries.
- `data: null` records a deletion, so it can win a merge on another device.
- `PUT` takes `{ "baseVersion": <version you merged from>, "entries": { ... } }` and returns the saved plan with the new version.

## Error format

Errors use RFC 9457 `application/problem+json`. Stack traces, credentials, and coordinates are never returned.

```json
{
  "type": "about:blank",
  "title": "Travel session not found",
  "status": 404,
  "detail": "No active travel session matches the supplied identifier.",
  "instance": "/api/v1/travel-sessions/00000000-0000-0000-0000-000000000000/recommendations"
}
```

Validation errors (`400`) add an `errors` object keyed by field name:

```json
{
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed. See 'errors' for field-level details.",
  "errors": { "location.latitude": "must be between -90 and 90" }
}
```

| Status | When | Title |
|---|---|---|
| 400 | Invalid JSON, validation failure, invalid coordinates, malformed session UUID | `Malformed request`, `Validation failed`, `Invalid path parameter` |
| 404 | Unknown session, or an expired session after retention ends | `Travel session not found` |
| 409 | Refresh while another refresh for the same session is running | `Refresh already in progress` |
| 409 | Refresh after the session was stopped | `Travel session stopped` |
| 410 | Refresh on an expired session within the retention window | `Travel session expired` |
| 410 | Stop on an expired session within the retention window | `Travel session expired` |
| 503 | Attraction provider unavailable | `Attraction provider unavailable` |
| 503 | Session capacity reached | `Travel session capacity reached` |
| 500 | Unexpected server error | `Internal server error` |

`429` is not used. The backend has no request rate limiter. Capacity is reported as `503` because it is a server-wide resource limit.

Stopping a session is idempotent. A repeated `DELETE` on a stopped session returns `204`.

## Session lifecycle and refresh rules

### Expiration

- A session expires after 30 minutes of inactivity. Each valid interaction extends it.
- Sessions have an absolute lifetime cap of 120 minutes. Activity cannot extend them beyond this.
- After expiry, the backend answers `410` for 30 more minutes, then `404`.
- Restarting the backend removes all sessions. The frontend must handle `404` by creating a new session.

### Refresh

- The first refresh in a session always evaluates recommendations (`UPDATED` or `NO_NEW_RESULTS`).
- A later refresh evaluates only when **both** are true:
  - The traveler has moved at least 250 m from the last successfully evaluated location.
  - At least 5 minutes have passed since the last successful evaluation.
- Otherwise the response is `UNCHANGED`. The session is extended, but the movement baseline and cooldown clock are not reset.
- Attractions returned in an earlier `UPDATED` response are excluded from later responses in the same session.
- A failed provider call does not count as an evaluation, so the next refresh tries again.

### Sequence

1. `POST /api/v1/travel-sessions` with the traveler's preferences. Store `sessionId`.
2. `POST /api/v1/travel-sessions/{sessionId}/recommendations` with the current location. Expect `UPDATED` (or `NO_NEW_RESULTS`).
3. While travel mode is active, send each new location to the same endpoint.
4. Handle the result:
   - `UPDATED`: show `recommendations`.
   - `UNCHANGED`: keep the current list. Do not retry.
   - `NO_NEW_RESULTS`: show "no new suggestions nearby". Do not retry.
5. On `503`, back off and retry the same location later.
6. On `404` or `410`, the session is gone. Create a new session.
7. `DELETE /api/v1/travel-sessions/{sessionId}` when travel mode ends.

## Responsibilities of the frontend and backend

- The browser geolocation permission prompt, and `navigator.geolocation.watchPosition`, belong to the React frontend.
- The frontend decides when to send location updates and should throttle them. The backend enforces the 250 m and 5 min rules and skips the rest.
- The backend does not track the traveler independently. It has no background location processing and sends no push notifications. While the page is closed, the backend does nothing.

## Example requests

Create a session:

```bash
curl -i -X POST http://localhost:8080/api/v1/travel-sessions \
  -H 'Content-Type: application/json' \
  -H 'Origin: http://localhost:5173' \
  -d '{"interests":["HISTORY","ART"],"maxBudget":50,"currencyCode":"EUR","availableMinutes":180}'
```

```json
{
  "sessionId": "b0f6ea0e-f9bc-4584-94fe-cf0dcb8a96f4",
  "status": "ACTIVE",
  "expiresAt": "2026-10-16T04:22:06Z"
}
```

Refresh with a location:

```bash
curl -s -X POST http://localhost:8080/api/v1/travel-sessions/$SESSION_ID/recommendations \
  -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":41.9028,"longitude":12.4964}}'
```

```json
{
  "sessionId": "b0f6ea0e-f9bc-4584-94fe-cf0dcb8a96f4",
  "refreshStatus": "UPDATED",
  "recommendations": [ { "placeId": "pantheon", "name": "Pantheon", "...": "see RecommendationDto" } ],
  "generatedAt": "2026-10-16T03:52:06Z"
}
```

Refresh that is too soon after the last one:

```json
{
  "sessionId": "b0f6ea0e-f9bc-4584-94fe-cf0dcb8a96f4",
  "refreshStatus": "UNCHANGED",
  "recommendations": [],
  "generatedAt": "2026-10-16T03:52:40Z"
}
```

Stop the session:

```bash
curl -i -X DELETE http://localhost:8080/api/v1/travel-sessions/$SESSION_ID
# HTTP/1.1 204 No Content
```

Stateless recommendations:

```bash
curl -s -X POST http://localhost:8080/api/v1/recommendations \
  -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":41.9028,"longitude":12.4964},"interests":["HISTORY"],"maxBudget":50,"currencyCode":"EUR","availableMinutes":180}'
```

Invalid coordinates (`400`):

```bash
curl -s -X POST http://localhost:8080/api/v1/travel-sessions/$SESSION_ID/recommendations \
  -H 'Content-Type: application/json' \
  -d '{"location":{"latitude":200,"longitude":12.4964}}'
```

## Configuration

| Property | Environment variable | Default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8080` |
| `travel.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |
| `attraction.provider` | `ATTRACTION_PROVIDER` | `mock` |
| `google.places.api-key` | `GOOGLE_PLACES_API_KEY` | none (required when provider is `google`) |
| `travel.ai.enabled` | `TRAVEL_AI_ENABLED` | `false` |
| `spring.ai.openai.api-key` | `OPENAI_API_KEY` | none (required when AI is enabled; also set `SPRING_AI_MODEL_CHAT=openai`) |
| `travel.session.minimum-movement-meters` | `TRAVEL_SESSION_MIN_MOVEMENT_METERS` | `250` |
| `travel.session.minimum-refresh-interval-minutes` | `TRAVEL_SESSION_MIN_REFRESH_INTERVAL_MINUTES` | `5` |
| `travel.session.inactivity-timeout-minutes` | `TRAVEL_SESSION_INACTIVITY_TIMEOUT_MINUTES` | `30` |
| `travel.session.max-lifetime-minutes` | `TRAVEL_SESSION_MAX_LIFETIME_MINUTES` | `120` |
| `travel.session.retention-after-expiry-minutes` | `TRAVEL_SESSION_RETENTION_MINUTES` | `30` |
| `travel.session.max-active-sessions` | `TRAVEL_SESSION_MAX_ACTIVE` | `100` |
| `travel.session.cleanup-interval-ms` | `TRAVEL_SESSION_CLEANUP_INTERVAL_MS` | `60000` |

Local development runs on mock attraction data with AI disabled. No API keys are needed.

## Limitations

- Sessions are in memory. A backend restart ends them, so the frontend must recover by creating a new session.
- The session ID is a bearer credential, with no authentication layer yet. Anyone holding it can refresh or stop that session. Use HTTPS in production.
- No OpenAPI document is generated. This document is the contract.
- AI personalization is not part of the refresh path yet. Recommendations are deterministic.
