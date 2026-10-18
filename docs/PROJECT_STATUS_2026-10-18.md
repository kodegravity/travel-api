# AI Travel Companion (ROAM / AI) — Project Status Audit

**Date:** 2026-10-18
**Scope:** Read-only audit. No application code, dependencies, commits, pushes, or deployments were changed or performed. No paid OpenAI or Google Places calls were made.

---

## 1. Repositories

| Repo | Location | Status |
|---|---|---|
| Backend (`kodegravity/travel-api`, branch `development`) | `/Users/sunny/IdeaProjects/travel-app` | Verified. Working tree clean. Three Maven services under `services/`. No root `pom.xml` or `src/`, so the monolith has been removed from the tree. |
| Frontend (React/Vite/Okta) | `/Users/sunny/WebstormProjects/travel-app-ui` | Verified source. **Not in its own Git repo.** `git rev-parse` resolves to a home-directory repo (`/Users/sunny`, remote `Surinder07/private`), and the whole app appears as untracked. **Process risk, P1.** |

---

## 2. Backend Audit (travel-app)

| Area | Finding | Status |
|---|---|---|
| `RecommendationController` (recommendation-service, internal) | `POST /internal/v1/recommendations`. Public `POST /api/v1/recommendations` lives in travel-session-service. | IMPLEMENTED |
| `TravelSessionController` | Create, refresh, and stop under `/api/v1/travel-sessions`. | IMPLEMENTED |
| `RecommendationService` / `DefaultRecommendationService` | Eligibility filters, dedupe via `excludedPlaceIds`, delegates to AI when enabled. | IMPLEMENTED |
| `RecommendationRankingService` | Deterministic weights: interest 40%, distance 25%, budget 20%, time 15%. | IMPLEMENTED, unit-tested |
| `AIRecommendationService` / `SpringAIRecommendationService` | Wired, but **disabled by default** (`TRAVEL_AI_ENABLED:false`). Requires `OPENAI_API_KEY` when enabled. | IMPLEMENTED / UNVERIFIED (no live runs) |
| `GooglePlacesAttractionProvider` | Enabled only with `attraction.provider=google`. Key is sent via header only. | IMPLEMENTED / UNVERIFIED (no live calls) |
| `MockAttractionProvider` | Default. Returns the same 10 hard-coded Rome places regardless of location. Users outside Rome get zero results. | IMPLEMENTED (dev only) |
| `TravelSessionRepository` | In-memory `ConcurrentHashMap`. Lost on restart. Breaks across multiple ECS tasks without sticky routing. | PARTIAL (not production-grade) |
| Session expiry | Idle 30 min, max lifetime 120 min, purge every 60 s. | IMPLEMENTED |
| Session dedupe | `recommendedPlaceIds` passed as excluded IDs. Movement gate 250 m, cooldown 5 min. | IMPLEMENTED |
| API exception handling | ProblemDetail responses (`title`, `detail`, `errors`). | IMPLEMENTED |
| CORS | Applies to `/api/v1/**` only, `allowCredentials(false)`. | IMPLEMENTED |
| Okta JWT validation | Issuer, expiry, and audience validators (`OktaJwtValidators`). Optional scope via `TRAVEL_REQUIRED_SCOPE`. | IMPLEMENTED / UNVERIFIED against a live Okta tenant |
| API authorization | `/api/v1/**` requires authentication. Stateless resource server. CSRF disabled (acceptable for bearer tokens). | IMPLEMENTED |
| Session ownership | `find()` filters by JWT `sub`. Other users get NOT_FOUND, so IDs can't be probed. | IMPLEMENTED |
| AI personalization in Active Travel Mode | Code path exists (session → remote recommendation → AI). Not active by default. No end-to-end proof. | PARTIAL / UNVERIFIED |

**Bugs and risks found**

1. **`RemoteRecommendationService.recommend`** (travel-session `client/RemoteRecommendationService.java`) calls `response.recommendations().size()` before the `response == null` check. A null body becomes an NPE (500) instead of a 503. **P1 bug.**
2. **No AI fallback.** When AI fails, the request returns 503. There is no fallback to deterministic ranking. An empty AI selection maps to `NO_NEW_RESULTS` even when eligible candidates exist. **P2.**
3. **Mock provider ignores location.** Acceptable for dev. **P1 before any non-dev demo.**
4. **In-memory sessions.** Lost on restart and not shared between ECS tasks. **P0 for multi-task production deployment.**
5. **README is stale.** It says it "contains only the initial application skeleton." **P3.**

---

## 3. Microservices Audit

| Service | Status | Location | Port | REST endpoints | Dockerfile | Tests (run today) | CI |
|---|---|---|---|---|---|---|---|
| Places Discovery Service | IMPLEMENTED | `services/places-discovery-service` | 8082 | `POST /internal/v1/places/search` | Yes (multi-stage) | 37 run, 37 passed | Yes |
| Recommendation Service | IMPLEMENTED | `services/recommendation-service` | 8081 | `POST /internal/v1/recommendations` | Yes (multi-stage) | 63 run, 63 passed | Yes |
| Travel Session Service | IMPLEMENTED | `services/travel-session-service` | 8080 | `POST /api/v1/recommendations`, `/api/v1/travel-sessions` (create, refresh, stop) | Yes (multi-stage) | 81 run, 81 passed | Yes |

- **Monolith:** removed from the tree. Only the three services remain.
- **Independence:** each has its own `pom.xml`, `mvnw`, and Dockerfile. Communication is HTTP via `RestClient`, forwarding the user's bearer token. Independent runs are UNVERIFIED (no live multi-service run was performed).
- **Docker Compose:** **NOT STARTED.** No `docker-compose` file exists. Running all services together is UNVERIFIED.

---

## 4. Frontend Audit (travel-app-ui)

| Feature | Evidence | Status |
|---|---|---|
| Login page | `src/pages/LoginPage.tsx` | IMPLEMENTED / UNVERIFIED (no live check) |
| Registration flow | No sign-up or registration code found in `src/`. | NOT STARTED |
| Okta SSO | `src/config/okta.ts`: Authorization Code + PKCE, `responseType: ['code']`, openid/profile/email, `sessionStorage`, autoRenew. Null-safe when env is missing. | IMPLEMENTED / UNVERIFIED (no live Okta) |
| Callback | `src/pages/AuthCallbackPage.tsx`, route `/login/callback` | IMPLEMENTED |
| Protected routes | `src/auth/ProtectedRoute.tsx`, `AuthGate.tsx`. Route `/` requires auth. | IMPLEMENTED |
| Access token attachment | `src/services/travelApi.ts` `send()` adds `Authorization: Bearer` when Okta is configured. | IMPLEMENTED |
| Logout | `Header.tsx` and `AuthGate.tsx` call `auth.logout()` / `logout()`. Signs out to `/login`. | IMPLEMENTED |
| Traveler preferences | `TravelPreferencesForm.tsx`, `useTravelPreferences.ts`, `travelPreferences.ts`. | IMPLEMENTED |
| Active Travel Mode | `useTravelSession.ts`: start / refresh / stop, phase state machine, abort handling, stale-response guards. | IMPLEMENTED |
| Browser geolocation | `services/geolocation.ts` `getCurrentLocation` and `watchLocation`. Watch runs only while tab is visible. | IMPLEMENTED / UNVERIFIED in a browser |
| Automatic recommendation refresh | Interval 5 min (`AUTO_REFRESH_INTERVAL_MS`), throttle 30 s, visibility catch-up. Unit-tested. | IMPLEMENTED |
| Proactive in-app notifications | `notifyNewPlaces()` exists in `services/notifications.ts`, but **it is never called** outside its own file. Permission request is wired in `TravelExplorerPage`. In-tab new-place alerts do not fire. | PARTIAL |
| Recommendation cards | `RecommendationCard.tsx` shows name, category, cost, duration, description. | IMPLEMENTED |
| Google Maps navigation | `RecommendationCard.tsx` uses `isSafeMapsUrl(mapsUrl)` to render a directions button. | IMPLEMENTED |
| Feedback controls | No feedback UI or API calls found. | NOT STARTED |
| Loading and error states | `RecommendationResults.tsx`, `TravelModeCard.tsx`, `describeTravelApiError`. | IMPLEMENTED |
| Mobile responsiveness | `global.css` has breakpoints at 640, 768, 960 px. Not verified in a real browser. | IMPLEMENTED / UNVERIFIED |

**Frontend tests (run today):** Vitest: 130 tests. **125 passed, 5 failed, across 2 files.**
- `src/pages/TravelExplorerSession.test.tsx`: 2 failures.
- `src/services/travelApi.test.ts`: 3 failures.
- All five fail on the same assertion: the request URL is `localhost:9080` but the test expects `localhost:8080`. This points to a local environment override (`VITE_API_BASE_URL`) rather than a code defect. Cause is **UNVERIFIED**. **P1: make tests independent of local env.**

**Typecheck (`tsc -p tsconfig.app.json --noEmit`):** no errors reported.

**Not run:** `npm run build` (writes `dist/`), lint, and Playwright (`e2e/live` needs live backend and Okta, so UNVERIFIED; `e2e/intercepted` was not run in this audit).

---

## 5. Authentication & Security Audit

| Control | Status | Notes |
|---|---|---|
| Okta SPA config | IMPLEMENTED / UNVERIFIED | Config is code-correct. Live tenant not checked. |
| Authorization Code + PKCE | IMPLEMENTED / UNVERIFIED | `okta.ts`. |
| Login and registration | PARTIAL | Login exists. Registration is not in-app (may be Okta-hosted; UNVERIFIED). |
| Resource server (JWT) | IMPLEMENTED | `SecurityConfig` in travel-session. |
| Issuer and audience validation | IMPLEMENTED | `OktaJwtValidators`. Audience value must match the Okta app (UNVERIFIED). |
| Protected endpoints | IMPLEMENTED | `/api/v1/**`. |
| Session ownership | IMPLEMENTED | Uses JWT `sub`. |
| Token expiration handling | IMPLEMENTED | `AuthProvider` handles expiry and error events. |
| CORS | IMPLEMENTED | Restricted to `/api/v1/**`. Origins must be set per environment (UNVERIFIED for prod). |
| Logout | IMPLEMENTED | Local sign-out. Okta session revocation not verified. |
| Secrets management | PARTIAL | Backend secrets use env vars and Secrets Manager names in ECS JSON. Frontend `.env.local` holds VITE_ values (not displayed). Gitignored. **Do not place secrets in VITE_ variables.** |
| Sensitive logging | UNVERIFIED | Not fully reviewed. |

**Security gaps before public deployment**
- **P0:** Sessions are in-memory, so ownership cannot be enforced across ECS tasks without sticky routing.
- **P1:** Null-body NPE in remote recommendation client (see §2).
- **P1:** `readonlyRootFilesystem` is not set in ECS task definitions.
- **P1:** Frontend lives untracked in a home-directory Git repo. Code review and history are not reliable.
- **P2:** Verify `TRAVEL_REQUIRED_SCOPE` and Okta audience config for each environment.

---

## 6. Docker & GitHub Actions Audit

| Item | Status | Evidence |
|---|---|---|
| Dockerfiles (3 services) | IMPLEMENTED | Multi-stage, pinned Temurin 21 Alpine, non-root UID/GID 10001, HEALTHCHECK via busybox wget, exec-form ENTRYPOINT. Tests run in image build. |
| Docker image builds run today | UNVERIFIED | Docker daemon not reachable. |
| Docker Compose | NOT STARTED | No file. |
| Maven CI | IMPLEMENTED | `.github/workflows/backend-ci.yml`: `./mvnw -B -ntp clean verify` per service. |
| Frontend CI | NOT STARTED | No workflow for the frontend. |
| Automated tests in CI | IMPLEMENTED (backend) | Runs in `verify`. |
| Docker build in CI | IMPLEMENTED | Per-service build. |
| Non-root check and smoke test | IMPLEMENTED | Smoke test on `/actuator/health` with fake Okta values. |
| Security scanning | IMPLEMENTED | Trivy with CRITICAL/HIGH fixable-vuln gate. |
| ECR push via OIDC | IMPLEMENTED / UNVERIFIED | Only on master/development. Role ARN with account ID is hard-coded. Not run. |
| ECS deploy automation | NOT STARTED | No deploy job. Task definitions are placeholders (`<ACCOUNT_ID>`, `<IMAGE_TAG>`). |

Note: `backend-ci.yml` triggers on `master` and `development`. The current branch is `development`.

---

## 7. AWS Infrastructure Audit

| Resource | Status | Evidence |
|---|---|---|
| ECR | UNVERIFIED | Referenced in CI. Not checked live. |
| ECS Fargate | PARTIAL (definitions only) | `infra/ecs/*.json` with placeholders. No cluster or service definition. |
| Application Load Balancer | NOT STARTED | No config. |
| VPC and security groups | NOT STARTED | No config. |
| S3 and CloudFront | NOT STARTED | No config for the frontend. |
| IAM | PARTIAL | OIDC role referenced in CI only. |
| Secrets Manager | PARTIAL | Secret names referenced in ECS JSON. |
| DynamoDB / Redis | NOT STARTED | Sessions are in-memory. |
| CloudWatch | PARTIAL | awslogs in task definitions. |
| Datadog | NOT STARTED | No config found. |
| Terraform | NOT STARTED | No Terraform files. |
| Auto scaling | NOT STARTED | No config. |
| HTTPS and DNS | NOT STARTED | No config. |

No live AWS access was used. **All deployed infrastructure is UNVERIFIED.**

---

## 8. Automated Tests

### Backend (executed today with `./mvnw -B -ntp test`)

| Service | Total | Passed | Failed | Skipped | Build |
|---|---|---|---|---|---|
| places-discovery-service | 37 | 37 | 0 | 0 | SUCCESS |
| recommendation-service | 63 | 63 | 0 | 0 | SUCCESS |
| travel-session-service | 81 | 81 | 0 | 0 | SUCCESS |

Run with `test`, not `verify`. Docker-based checks did not run.

### Frontend (executed today)

| Check | Result |
|---|---|
| Vitest | 130 total, 125 passed, 5 failed (env URL mismatch, see §4) |
| Typecheck | No errors |
| Build / lint | Not run |
| Playwright intercepted | Not run |
| Playwright live | UNVERIFIED (needs backend and Okta) |

**E2E status:** UNVERIFIED for both live and intercepted runs.

---

## 9. Project Status Matrix

| Component | Status | Evidence | Pending Work | Priority |
|---|---|---|---|---|
| Recommendation Service | IMPLEMENTED / UNVERIFIED | 63 passing unit tests | Live run and integration tests | P1 |
| Travel Session Service | IMPLEMENTED / UNVERIFIED | 81 passing unit tests | Fix null-body NPE; live run | P1 |
| Places Discovery Service | IMPLEMENTED / UNVERIFIED | 37 passing tests | Live Google Places check (approval needed) | P1 |
| Session persistence | PARTIAL | In-memory `ConcurrentHashMap` | Redis/DynamoDB or sticky routing | P0 |
| AI personalization | PARTIAL | Code present, disabled by default | Enable and verify with approved key | P2 |
| Mock vs Google provider | PARTIAL | Mock ignores location | Google provider verification | P1 |
| Docker images | IMPLEMENTED / UNVERIFIED | Dockerfiles present, daemon unreachable | Build and Trivy run | P1 |
| Docker Compose | NOT STARTED | No file | Create compose for local stack | P2 |
| Backend CI (Maven + Trivy) | IMPLEMENTED | `backend-ci.yml` | Confirm green run on GitHub | P1 |
| Frontend CI | NOT STARTED | No workflow | Add Vitest, typecheck, build, lint | P1 |
| ECR push (OIDC) | IMPLEMENTED / UNVERIFIED | CI job exists | Verify role and account | P1 |
| ECS deployment | PARTIAL | Task JSON only | Cluster, service, deploy job | P1 |
| AWS infrastructure (ALB, VPC, DNS, TLS) | NOT STARTED | No IaC | Terraform or CDK | P1 |
| Okta login (PKCE) | IMPLEMENTED / UNVERIFIED | `okta.ts`, `AuthProvider.tsx` | Live login test | P0 |
| Registration flow | NOT STARTED | No code | Decide Okta-hosted vs in-app | P1 |
| Protected routes and logout | IMPLEMENTED | `ProtectedRoute.tsx`, `Header.tsx` | Browser test | P1 |
| Access token attachment | IMPLEMENTED | `travelApi.ts` | Verify with live API | P1 |
| Traveler preferences | IMPLEMENTED | `TravelPreferencesForm.tsx` | Browser test | P2 |
| Active Travel Mode | IMPLEMENTED / UNVERIFIED | `useTravelSession.ts`, tests | Live and browser test | P1 |
| Browser geolocation | IMPLEMENTED / UNVERIFIED | `geolocation.ts` | Real-device test | P1 |
| Auto refresh (5 min) | IMPLEMENTED | `useTravelSession.ts` | None beyond tests | P2 |
| Proactive notifications | PARTIAL | `notifyNewPlaces` never called | Wire into refresh result | P2 |
| Recommendation cards | IMPLEMENTED | `RecommendationCard.tsx` | Visual QA | P2 |
| Google Maps navigation | IMPLEMENTED | `isSafeMapsUrl` | None | P3 |
| Feedback controls | NOT STARTED | No code | Design and API | P2 |
| Loading and error states | IMPLEMENTED | `RecommendationResults.tsx` | None | P3 |
| Mobile responsiveness | IMPLEMENTED / UNVERIFIED | Media queries | Real-device QA | P2 |
| Frontend tests | PARTIAL | 125/130 pass | Fix 5 env-dependent failures | P1 |
| Frontend repo hygiene | PARTIAL | Untracked, home-directory repo | Create its own repo | P1 |
| Docs | PARTIAL | `docs/*.md` present; README stale | Update README | P3 |
| Monitoring (CloudWatch, Datadog) | PARTIAL | awslogs only | Alarms and dashboards | P1 |

---

## 10. Prioritized Action Plan

### A. Completed and Verified Today
- Backend unit tests: 37 + 63 + 81 = **181 tests passed, 0 failed**.
- Frontend typecheck passed.
- Frontend tests: 125 of 130 passed.
- Backend CI config, Dockerfiles, and ECS definitions exist and were reviewed.
- Session ownership, expiry, dedupe, and ProblemDetail error handling are implemented in code.

### B. In Progress
- Active Travel Mode (refresh, geolocation, dedupe) is implemented but not verified in a browser or live stack.
- AI personalization is code-complete but disabled by default.
- Google Places provider is code-complete but not verified live.
- ECS task definitions exist, but there is no deploy path.

### C. Pending
- Persistent session store (Redis or DynamoDB).
- Docker Compose for the full local stack.
- Frontend CI workflow.
- Registration flow decision.
- Feedback controls.
- Wire `notifyNewPlaces` into the refresh path.
- Terraform or CDK for ALB, VPC, DNS, TLS, and autoscaling.
- Datadog or equivalent monitoring.

### D. Bugs and Security Risks
1. NPE on null body in `RemoteRecommendationService` (P1).
2. In-memory sessions break under multiple tasks (P0).
3. Five frontend tests depend on local port config (P1).
4. Frontend is untracked, in a home-directory repo (P1).
5. Mock provider ignores location (P1 for non-dev).
6. No AI fallback to deterministic ranking (P2).
7. `readonlyRootFilesystem` not set (P1).
8. Hard-coded role ARN with account ID in CI (P2).
9. Okta audience and scope must be verified per environment (P1).

### E. Production Readiness
- **Docker:** images well built, but not verified in this audit (daemon unreachable). Compose missing.
- **CI/CD:** backend CI is implemented. Frontend CI and deploy are missing.
- **AWS:** no verified infrastructure. Mostly NOT STARTED.
- **Session persistence:** P0 blocker.
- **Monitoring:** logging to CloudWatch only.
- **Security:** auth code is solid. Gaps are listed in §5.
- **Testing:** backend strong at unit level. No integration or E2E evidence. Frontend has 5 failing tests.

### F. Recommended Next 5 Tasks (dependency order)

1. **Fix the null-body NPE and frontend env-dependent tests.** Small.
   - Repo: backend (NPE) and frontend (tests).
   - Acceptance: null body returns 503; the 5 frontend tests pass with any local `VITE_API_BASE_URL`.
   - Dependencies: none.
2. **Move the frontend into its own Git repo and add a frontend CI workflow** (Vitest, typecheck, build, lint). Medium.
   - Repo: frontend.
   - Acceptance: repo has its own remote, CI is green on push.
   - Dependencies: none.
3. **Add Docker Compose** for the three services plus frontend. Medium.
   - Repo: backend.
   - Acceptance: `docker compose up` runs all services; health checks pass; a live request returns recommendations with the mock provider.
   - Dependencies: task 1.
4. **Replace in-memory session store** with Redis or DynamoDB. Large.
   - Repo: backend.
   - Acceptance: sessions survive restart and work across two instances; ownership and expiry tests still pass.
   - Dependencies: task 3.
5. **Wire proactive notifications and verify Active Travel Mode in a browser.** Medium.
   - Repo: frontend.
   - Acceptance: `notifyNewPlaces` fires on `UPDATED` when the tab is hidden; Playwright intercepted tests cover geolocation, refresh, and stop.
   - Dependencies: tasks 1 and 2.

---

## 11. Final Summary

1. **Current phase:** Backend microservices are split and unit-tested. Frontend Active Travel Mode is built. Integration, deployment, and infrastructure are not yet started.
2. **Components:** Backend (of 12 core items reviewed) — ~8 complete or implemented, 3 partial, 1 missing. Frontend (of 19 items) — ~12 implemented, 3 partial, 2 missing (registration, feedback), others unverified. Infra — 11 missing or partial. Counts are indicative, taken from §9.
3. **Most important blocker:** In-memory session storage (P0). It prevents multi-instance production deployment and breaks dedupe and ownership across tasks.
4. **Works end to end locally?** **UNVERIFIED.** Backend services pass unit tests, but no multi-service run was performed. Docker Compose is missing.
5. **Production-ready?** **No.** Blockers: session persistence, no infrastructure, no deploy path, frontend CI missing, and the untracked frontend repo.
6. **The ONE task to implement next:** Fix the null-body NPE and the 5 env-dependent frontend tests, which are small and unblock reliable CI. Then move on to the persistent session store.

---

*This report is an audit only. Nothing was committed or pushed.*
