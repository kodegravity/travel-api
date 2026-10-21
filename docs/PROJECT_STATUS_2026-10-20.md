# ROAM / AI – Project Status

**Date:** 2026-10-20
**Replaces:** the 2026-10-18 audit. Most of its P0 and P1 items are now done (see §2).
**Method:** code and Terraform review of both repositories, plus local test runs on this date. No paid API calls were made. Live AWS resources were not queried; "live" values come from the Terraform configuration and state.

---

## 1. Repositories

| Repo | Local folder | Branch | State |
|---|---|---|---|
| Backend `kodegravity/travel-api` | `/Users/sunny/IdeaProjects/travel-app` | `development` | Three Spring Boot services, Terraform, CI |
| Frontend `kodegravity/travel-ui` | `/Users/sunny/WebstormProjects/travel-app-ui` | `main` | Own Git repo. Push to `main` deploys to CloudFront. |

---

## 2. Resolved since the 2026-10-18 audit

| Audit item | Resolution |
|---|---|
| In-memory sessions (P0) | DynamoDB `roam-travel-sessions` through `DynamoDbTravelSessionRepository` |
| Null-body NPE in `RemoteRecommendationService` (P1) | The null check now runs before the body is read |
| No AI fallback (P2) | 10s `TRAVEL_AI_TIMEOUT`, then deterministic ranking |
| Mock provider ignores location (P1) | Live ECS uses Google Places, with daily caps |
| No Terraform / AWS infrastructure | ECS, API Gateway, DynamoDB, SNS/SQS, S3 + CloudFront, and Secrets Manager modules in `infra/terraform` |
| Secrets not wired into tasks | ECS task definitions read OpenAI, Google Places, and Ticketmaster keys from Secrets Manager |
| Frontend untracked in a home-directory repo (P1) | Its own repo, `kodegravity/travel-ui` |
| No frontend CI | `deploy.yml`: typecheck, lint, Vitest, build, then deploy |
| 5 env-dependent frontend tests (P1) | All frontend tests pass |
| Monitoring was CloudWatch only | Logs forwarded to Datadog; SQS alarms emailed through SNS |
| Registration flow undecided | Okta-hosted sign-in and registration |
| Proactive notifications never fired | Fixed 2026-10-20 (`travel-ui` 39cbb2c): new places notify from a background tab through the service worker |

---

## 3. What is live

From the Terraform configuration and state (`infra/terraform/*`, local state).

| Area | Live setup |
|---|---|
| Entry point | API Gateway HTTP API `roam-ai-api` with an Okta JWT authorizer, through a VPC Link to travel-session via Cloud Map. No ALB. |
| Compute | ECS Fargate cluster `ai-travel-app`: three services, 0.25 vCPU and 512 MB each, Service Connect between them |
| Places | Google Places (New), Place Details, Photos, and Routes, each with a daily cap |
| AI | OpenAI on for ranking (`TRAVEL_AI_ENABLED=true`), chat, and live alerts, each with a daily cap |
| Events | Ticketmaster (no coverage in India) |
| Messaging | SNS topic `roam-travel-events`, two SQS queues with DLQs, transactional outbox (`TRAVEL_MESSAGING_ENABLED=true`) |
| Frontend | S3 + CloudFront at https://dpitnqmwb86ju.cloudfront.net |
| Logs | CloudWatch, forwarded to Datadog |

---

## 4. Features

| Feature | Where | Status |
|---|---|---|
| Okta sign-in (PKCE), protected routes, logout | frontend `auth/`, `config/okta.ts` | Done |
| City explore, place details and photos | `/explore/:cityId`, `/places/:cityId/:placeId` | Done |
| Trip builder, route optimization | `/trip/:cityId`, `/trip/:cityId/route` | Done |
| Itineraries with replanning and detour suggestions | `/trip/:cityId/itinerary`, `/itinerary` | Done; stored in the browser only |
| Active Travel Mode (location watch, auto refresh, dedupe) | `/travel-mode`, `useTravelSession.ts` | Done |
| New-place alerts | In-app alert, plus a system notification while the tab is in the background | Done (2026-10-20). Needs the page open. |
| Live event alerts (Ticketmaster, AI-written) | Travel Mode | Done |
| "Ask ROAM" chat with weather | `/assistant`, `/api/v1/chat` | Done |
| Event-driven messaging | `docs/messaging.md` | Done; the notification consumer only logs |
| Feedback controls | – | Not started |

---

## 5. Tests (run 2026-10-20)

| Suite | Result |
|---|---|
| places-discovery-service (`./mvnw test`) | 75 run, 0 failed |
| recommendation-service | 123 run, 0 failed |
| travel-session-service | 153 run, 0 failed, 14 skipped (DynamoDB and LocalStack tests run only with `DYNAMODB_TEST_ENDPOINT` / `LOCALSTACK_ENDPOINT` set) |
| Frontend Vitest | 248 run, 0 failed |
| Frontend typecheck and lint | Pass |
| Playwright (`e2e/intercepted`, `e2e/live`) | Not run |

One unhandled error appears in the Vitest run. It comes from an uncommitted local change in `TravelExplorerPage.tsx` that calls `scrollIntoView(...)`, which jsdom lacks. `scrollIntoView?.(...)` fixes it.

---

## 6. Pending work

| # | Item | Why it matters | Priority |
|---|---|---|---|
| 1 | Move Terraform state to a remote backend (S3) | State exists on one laptop only. `terraform.yml` needs `backend.hcl` and `TF_AWS_ROLE_ARN` before CI can plan or apply. | P1 |
| 2 | Set `readonlyRootFilesystem` on the ECS tasks | Container hardening; not set on any task | P1 |
| 3 | End-to-end verification | Playwright has not run against the live stack. Location and notifications were tested by hand on a phone only. | P1 |
| 4 | Web Push for notifications | Today's alerts need the page open, and phones may pause background tabs. The SQS notification consumer is the hook for it. | P2 |
| 5 | Daily caps are per task and reset on restart | Provider-side quotas are the real hard limit | P2 |
| 6 | Docker Compose for the local stack | No one-command local run | P2 |
| 7 | Feedback controls (UI and API) | Not started | P2 |
| 8 | Itinerary sync | Itineraries live in the browser only; `ItineraryUpdated` is not produced yet | P2 |
| 9 | ECR role ARN with the account ID is hard-coded in `backend-ci.yml` | Should be a repository variable, like `TF_AWS_ROLE_ARN` | P3 |
| 10 | README is out of date | It lists only the first endpoints, not chat, events, routes, or messaging | P3 |
| 11 | Datadog follow-ups (from 2026-10-18, not rechecked) | Rotate the API keys that were shared; logs show `service: ecs` instead of each service name | P3 |

---

## 7. Next steps

1. Remote Terraform state, so infrastructure isn't tied to one machine and CI can plan.
2. Run the Playwright intercepted suite in `deploy.yml`, and run the live suite by hand once against the deployed stack.
3. Set `readonlyRootFilesystem`, with a writable `/tmp` volume if a service needs one.
4. Decide whether Web Push is worth building now (it means VAPID keys, a subscription store, and a sender behind the notification queue).
