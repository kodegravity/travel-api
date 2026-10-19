# ROAM / AI – Architecture

ROAM / AI is a location-aware travel companion. A traveler shares their location, interests, budget, and available time, and gets the nearby attractions they can realistically fit in, ranked best first. Active Travel Mode refreshes recommendations as the traveler moves.

The system has two repositories:

| Repository | Stack | Purpose |
|---|---|---|
| `travel-app` (backend) | Java 21, Spring Boot 4, Spring Security (Okta JWT), Spring AI (OpenAI), Google Places | Three independent microservices |
| `travel-app-ui` (frontend) | React 19, TypeScript, Vite, `@okta/okta-auth-js`, react-router-dom | Traveler UI, Okta sign-in |

## System overview

```
Browser (React, Okta PKCE)
   │  Authorization: Bearer <Okta access token>
   ▼
ALB  (or API Gateway + VPC Link, Terraform-defined)
   │
   ▼
travel-session-service :8080      public API, session lifecycle, CORS
   │  forwards bearer token + X-Request-Id
   ▼
recommendation-service :8081      /internal/v1/**  filters, ranking, optional AI
   │                     │
   │                     └──► OpenAI (optional, Spring AI)
   ▼
places-discovery-service :8082    /internal/v1/**  attraction and route discovery
   │
   ├──► Google Places (New), Google Routes
   └──► Mock providers (default for local and Rome)
```

Service-to-service calls use `RestClient`. Each call forwards the caller's validated Okta token and the `X-Request-Id` header.

## Backend services

### travel-session-service (port 8080)

- **Role:** the only service the frontend calls directly. Owns Active Travel Mode sessions and enforces ownership.
- **Public endpoints:**
  - `POST /api/v1/travel-sessions`, start a session
  - `POST /api/v1/travel-sessions/{sessionId}/recommendations`, refresh recommendations
  - `DELETE /api/v1/travel-sessions/{sessionId}`, stop a session
  - `/api/v1/recommendations`, `/api/v1/routes`
- **Key classes:**
  - `controller/` (REST), `service/TravelSessionService`
  - `client/RemoteRecommendationService`, `client/RouteClient`, `client/PlaceDetailsClient`
  - `client/BearerTokenForwardingInterceptor`
  - `repository/` with `DynamoDbTravelSessionRepository` (deployed) and `InMemoryTravelSessionRepository` (local)
  - `config/WebCorsConfig`
- **Error mapping:** a recommendation failure becomes `503 Attraction provider unavailable` (RFC 9457 problem JSON).

### recommendation-service (port 8081)

- **Role:** eligibility filtering, deterministic ranking, and optional AI selection.
- **Internal endpoints:** `POST /internal/v1/recommendations`, `/internal/v1/routes`.
- **Key classes:**
  - `service/DefaultRecommendationService` (orchestration)
  - `service/RecommendationRankingService` (deterministic scoring)
  - `service/SpringAIRecommendationService` (optional OpenAI selection, gated by `TRAVEL_AI_ENABLED`)
  - `provider/RemotePlacesAttractionProvider` (calls places-discovery)
  - `provider/RouteClient`, `provider/PlaceDetailsClient`
- **Security:** `/internal/v1/**` requires an authenticated Okta token (or a scope set by `TRAVEL_REQUIRED_SCOPE`).

### places-discovery-service (port 8082)

- **Role:** attraction discovery and route estimates. Owns all Google Places calls.
- **Internal endpoints:**
  - `POST /internal/v1/places/search`
  - `GET /internal/v1/places/{placeId}`, `/internal/v1/places/{placeId}/photo`
  - `POST /internal/v1/routes/optimize`
- **Providers (selected by `ATTRACTION_PROVIDER`):**
  - `GooglePlacesAttractionProvider` or `MockAttractionProvider` (`ATTRACTION_PROVIDER=mock` for Rome)
  - `GooglePlaceDetailsProvider` or `MockPlaceDetailsProvider`
  - `GoogleRoutesProvider` or `EstimatedRouteProvider`

## Frontend (`travel-app-ui`)

- **Auth:** Okta SPA with Authorization Code + PKCE. Tokens are stored in `sessionStorage` (`src/config/okta.ts`).
- **Pages:** sign-in and registration, traveler preferences, Active Travel Mode, recommendation cards, feedback.
- **Client code:** `src/services/` (API clients), `src/hooks/`, `src/components/`.
- **Testing:** Vitest unit tests, Playwright e2e in `e2e/`.
- **Hosting:** static build in S3, served by CloudFront (`infra/terraform/frontend`).

## Data

- **Travel sessions:** DynamoDB table `sessions` (`infra/terraform/session-store`), accessed through `DynamoDbTravelSessionRepository`.
- **Secrets:** AWS Secrets Manager holds the OpenAI and Google Places keys (`infra/terraform/secrets`). The task definitions do not reference them yet, so wiring them in is pending.

## AWS deployment

| Component | Definition | Notes |
|---|---|---|
| ECS Fargate cluster `ai-travel-app` | `infra/terraform/ecs-services` | Three services, Service Connect namespace |
| Application Load Balancer | `infra/terraform/internal-routes` | Default route to travel-session; rules for `/internal/v1/*` to recommendation and places-discovery |
| API Gateway (HTTP API) + VPC Link | `infra/terraform/api-gateway` | Okta JWT authorizer, route to travel-session |
| DynamoDB | `infra/terraform/session-store` | Travel sessions |
| S3 + CloudFront | `infra/terraform/frontend` | Frontend hosting |
| Secrets Manager | `infra/terraform/secrets` | API keys |
| ECR | `backend-ci.yml` | Images per service, pushed on master and development |

Terraform state is currently local. Live status of the API Gateway and frontend stacks has not been verified.

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
| Attraction provider | places-discovery | `ATTRACTION_PROVIDER` | `mock` (live ECS is also `mock`) |
| AI selection | recommendation | `TRAVEL_AI_ENABLED` | `false` |
| Google Places key | places-discovery | from Secrets Manager | required for Google mode |
| OpenAI key | recommendation | from Secrets Manager | required when AI is enabled |
