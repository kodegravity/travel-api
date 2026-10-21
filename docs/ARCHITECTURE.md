# ROAM / AI – Architecture

ROAM / AI is a location-aware travel companion. A traveler shares their location, interests, budget, and available time, and gets the nearby attractions they can realistically fit in, ranked best first. Active Travel Mode refreshes recommendations as the traveler moves.

The system has two repositories:

| Repository | Stack | Purpose |
|---|---|---|
| `travel-api` (backend, local folder `travel-app`) | Java 21, Spring Boot 4, Spring Security (Okta JWT), Spring AI (OpenAI), Google Places | Three independent microservices |
| `travel-ui` (frontend, local folder `travel-app-ui`) | React 19, TypeScript, Vite, `@okta/okta-auth-js`, react-router-dom | Traveler UI, Okta sign-in |

## System overview

```
Browser (React, Okta PKCE)
   │  Authorization: Bearer <Okta access token>
   ▼
API Gateway HTTP API (Okta JWT authorizer) ──► VPC Link ──► Cloud Map
   │
   ▼
travel-session-service :8080      public API, session lifecycle, CORS
   │  forwards bearer token + X-Request-Id          │
   ▼                                                └──► SNS/SQS travel events (outbox, optional)
recommendation-service :8081      /internal/v1/**  filters, ranking, AI selection, chat, live alerts
   │                     │
   │                     ├──► OpenAI (Spring AI; falls back to ranking or rules)
   │                     └──► Open-Meteo weather (chat)
   ▼
places-discovery-service :8082    /internal/v1/**  attractions, routes, events
   │
   ├──► Google Places (New), Google Routes
   ├──► Ticketmaster Discovery (live events)
   └──► Mock providers (default for local and Rome)
```

Service-to-service calls use `RestClient` over ECS Service Connect. Each call forwards the caller's validated Okta token and the `X-Request-Id` header.

## Backend services

### travel-session-service (port 8080)

- **Role:** the only service the frontend calls directly. Owns Active Travel Mode sessions and enforces ownership.
- **Public endpoints:**
  - `POST /api/v1/travel-sessions`, start a session
  - `POST /api/v1/travel-sessions/{sessionId}/recommendations`, refresh recommendations. With `eventTypes`, the response also carries live event alerts (`liveSuggestions`).
  - `DELETE /api/v1/travel-sessions/{sessionId}`, stop a session
  - `GET /api/v1/places/{placeId}`, `GET /api/v1/places/{placeId}/photo`, proxied to places-discovery
  - `POST /api/v1/routes/optimize`
  - `POST /api/v1/recommendations`, stateless search (no session)
  - `POST /api/v1/chat`, "Ask ROAM" chat
- **Key classes:**
  - `controller/` (REST), `service/TravelSessionService`
  - `client/RemoteRecommendationService`, `client/RouteClient`, `client/PlaceDetailsClient`
  - `client/BearerTokenForwardingInterceptor`
  - `repository/` with `DynamoDbTravelSessionRepository` (deployed) and `InMemoryTravelSessionRepository` (local)
  - `config/WebCorsConfig`
  - Messaging: a DynamoDB transactional outbox, a relay that publishes to SNS, and an SQS notification consumer. Off unless `TRAVEL_MESSAGING_ENABLED=true` (see [messaging.md](messaging.md)).
- **Error mapping:** a recommendation failure becomes `503 Attraction provider unavailable` (RFC 9457 problem JSON).

### recommendation-service (port 8081)

- **Role:** eligibility filtering, deterministic ranking, AI selection, the "Ask ROAM" chat, and AI-written live event alerts.
- **Internal endpoints:**
  - `POST /internal/v1/recommendations`
  - `POST /internal/v1/chat`
  - `POST /internal/v1/live-suggestions`
  - `POST /internal/v1/routes/optimize`
  - `GET /internal/v1/places/{placeId}` and `/photo` (proxied to places-discovery)
- **Key classes:**
  - `service/DefaultRecommendationService` (orchestration)
  - `service/RecommendationRankingService` (deterministic scoring)
  - `service/SpringAIRecommendationService` (OpenAI selection, gated by `TRAVEL_AI_ENABLED`). After `TRAVEL_AI_TIMEOUT` (10s live) or on failure, it falls back to the deterministic ranking.
  - `chat/ChatService` (weather from `WeatherClient` plus cached nearby places; rule-based answer when the AI is off, capped, slow, or failing)
  - `live/LiveSuggestionService` (turns nearby events into short alerts)
  - `provider/RemotePlacesAttractionProvider` (calls places-discovery)
  - `provider/RouteClient`, `provider/PlaceDetailsClient`
- **Security:** `/internal/v1/**` requires an authenticated Okta token (or a scope set by `TRAVEL_REQUIRED_SCOPE`).

### places-discovery-service (port 8082)

- **Role:** attraction discovery, route estimates, and nearby events. Owns all Google and Ticketmaster calls.
- **Internal endpoints:**
  - `POST /internal/v1/places/search`
  - `GET /internal/v1/places/{placeId}`, `/internal/v1/places/{placeId}/photo`
  - `POST /internal/v1/routes/optimize`
  - `GET /internal/v1/events/nearby`
- **Providers (selected by `ATTRACTION_PROVIDER` and `EVENTS_PROVIDER`):**
  - `GooglePlacesAttractionProvider` or `MockAttractionProvider` (`ATTRACTION_PROVIDER=mock` for Rome)
  - `GooglePlaceDetailsProvider` or `MockPlaceDetailsProvider`
  - `GoogleRoutesProvider` or `EstimatedRouteProvider`
  - `TicketmasterEventProvider`, `SampleEventProvider`, or `NoEventProvider`
- **Daily caps:** every paid call has a per-task daily cap (`GOOGLE_PLACES_MAX_CALLS_PER_DAY` and similar). Counters reset on restart.

## Frontend (`travel-ui`)

- **Auth:** Okta SPA with Authorization Code + PKCE. Tokens are stored in `sessionStorage` (`src/config/okta.ts`).
- **Sign-in and registration:** Okta's hosted page (`LoginPage.tsx`). Credentials never reach the app. The callback is `/login/callback`.
- **Routes** (`src/App.tsx`): `/` home, `/explore/:cityId`, `/places/:cityId/:placeId`, `/trip/:cityId`, `/trip/:cityId/route`, `/trip/:cityId/itinerary`, `/itinerary`, `/travel-mode`, `/assistant` (Ask ROAM chat). `/explore` and `/trip` redirect; unknown paths redirect to `/`.
- **Backend calls** (`src/services/travelApi.ts`): `/api/v1/recommendations`, `/api/v1/travel-sessions` (start, refresh, stop), `/api/v1/places`, `/api/v1/routes/optimize`, `/api/v1/chat`.
- **Active Travel Mode** (`hooks/useTravelSession.ts`): watches location while the tab is visible, refreshes after 25 m of movement (at most every 30 s) and every 5 minutes. New places show as an in-app alert, pinned to the bottom on phones.
- **Notifications** (`services/notifications.ts`, `public/sw.js`): once the traveler allows them, new places and live events raise a system notification while the tab is in the background. They go through the service worker, which Android Chrome requires. Monitoring continues in the background only while notifications are allowed. This is not Web Push: the page must stay open. On iOS it works only from a Home Screen app.
- **Itineraries:** built and replanned in the browser (`services/itinerary.ts`), with detour suggestions along each leg. Stored in the browser only.
- **Maps:** Google Maps JS SDK with `VITE_GOOGLE_MAPS_API_KEY` and `VITE_GOOGLE_MAPS_MAP_ID`, used by `components/explorer/PlacesMap.tsx`.
- **Client code:** `src/services/` (API clients, geolocation, route planning), `src/hooks/`, `src/components/`.
- **Testing:** Vitest unit tests. Playwright specs in `e2e/` (`intercepted` and `live`).
- **Hosting:** static build in S3, served by CloudFront (`infra/terraform/frontend`). The `travel-ui` repo's `deploy.yml` runs typecheck, lint, Vitest, and build, then deploys on push to `main` (OIDC).

## Data

- **Travel sessions:** DynamoDB table `roam-travel-sessions` (hash key `sessionId`, on-demand billing) in `infra/terraform/session-store`, accessed through `DynamoDbTravelSessionRepository`. The same table holds the messaging outbox (`outbox#`) and processed-message (`processed#`) items.
- **Secrets:** AWS Secrets Manager holds the OpenAI, Google Places, Ticketmaster, and Google Maps browser keys. The ECS task definitions (`infra/terraform/ecs-services`) read them through `secrets`. The Ticketmaster secret is JSON and is read by its `ticketmaster-api-key` field.
- **Itineraries:** browser only. `ItineraryUpdated` exists as an event contract but is not produced yet.

## AWS deployment

| Component | Definition | Notes |
|---|---|---|
| ECS Fargate cluster `ai-travel-app` | `infra/terraform/ecs-services` | Three services (0.25 vCPU, 512 MB each), Service Connect namespace, secrets wired in |
| API Gateway (HTTP API) + VPC Link | `infra/terraform/api-gateway` | `roam-ai-api`. Okta JWT authorizer, `HTTP_PROXY` to travel-session through Cloud Map. The only public entry point (the ALB was removed). |
| DynamoDB | `infra/terraform/session-store` | `roam-travel-sessions`: sessions and the messaging outbox |
| SNS + SQS | `infra/terraform/messaging` | Topic `roam-travel-events`, queues `roam-recommendation-events` and `roam-notification-events` with DLQs, CloudWatch alarms emailed through an alerts topic |
| S3 + CloudFront | `infra/terraform/frontend` | Frontend hosting |
| Secrets Manager | `infra/terraform/secrets` | API keys |
| ECR | `backend-ci.yml` | Images per service, pushed on master and development |
| Logs | CloudWatch `/ecs/ai-travel-companion-<service>` | Forwarded to Datadog by the Datadog Forwarder Lambda (set up outside Terraform) |

Terraform state is local (gitignored), and applies are run by hand from each module. `infra/ecs/*.json` are reference templates only.

## Security

- **Authentication:** every backend service validates Okta JWTs (issuer and audience set via `OKTA_ISSUER` and `OKTA_AUDIENCE`).
- **Authorization:** travel-session checks session ownership. Internal endpoints require an authenticated token.
- **Containers:** non-root user (`10001:10001`), multi-stage Dockerfiles on Temurin 21 Alpine.
- **Secrets:** no keys in code or in Terraform state. Values are set directly in Secrets Manager.

## CI/CD

- `.github/workflows/backend-ci.yml`: Maven build and tests per service, Docker build, health smoke test, Trivy scan (blocks on fixable CRITICAL/HIGH), ECR push on master and development. Uses OIDC.
- `.github/workflows/terraform.yml`: `fmt`, `validate`, and `plan` on pull requests. `apply` on master, gated by the `production` environment. Requires remote state (`backend.hcl`) and the `TF_AWS_ROLE_ARN` variable.

## Configuration reference

| Setting | Service | Env var | Default |
|---|---|---|---|
| Okta issuer and audience | all | `OKTA_ISSUER`, `OKTA_AUDIENCE` | required |
| Required scope | all | `TRAVEL_REQUIRED_SCOPE` | unset (any valid token) |
| Attraction provider | places-discovery | `ATTRACTION_PROVIDER` | `mock` (live ECS: `google`) |
| Events provider | places-discovery | `EVENTS_PROVIDER` | live ECS: `ticketmaster` |
| AI selection | recommendation | `TRAVEL_AI_ENABLED` | `false` (live ECS: `true`) |
| AI timeout | recommendation | `TRAVEL_AI_TIMEOUT` | live ECS: `10s` |
| Messaging | travel-session, recommendation | `TRAVEL_MESSAGING_ENABLED` | `false` (live ECS: `true`) |
| Google Places key | places-discovery | from Secrets Manager | required for Google mode |
| OpenAI key | recommendation | from Secrets Manager | required when AI is enabled |
| Ticketmaster key | places-discovery | from Secrets Manager | required for Ticketmaster events |
| Frontend Okta and Maps keys | UI build | `VITE_OKTA_*`, `VITE_GOOGLE_MAPS_*` | public values only |
