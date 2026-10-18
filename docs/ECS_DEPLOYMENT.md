# ECS Fargate deployment

Status (2026-10-18): deployed to AWS us-east-2 (cluster `ai-travel-app`). Public base URL: `https://t1mmzzwy99.execute-api.us-east-2.amazonaws.com` (API Gateway HTTP API, HTTPS). The ALB was removed on 2026-10-18.

```
Internet -- HTTPS --> API Gateway (JWT authorizer, throttling) -- VPC link --> travel-session :8080
travel-session --Service Connect--> recommendation :8081 --Service Connect--> places-discovery :8082
```

Deployed resources, all managed by Terraform with local state:

| Resource | Name | Terraform |
|---|---|---|
| API Gateway HTTP API | `roam-ai-api` (stage `$default`, 5 req/s, burst 10) | `infra/terraform/api-gateway` |
| VPC link and its security group | `roam-ai-api`, `roam-ai-api-vpc-link` | `infra/terraform/api-gateway` |
| Access logs | `/aws/apigateway/roam-ai-api` (14-day retention) | `infra/terraform/api-gateway` |
| ECS task definitions and services | `travel-session`, `recommendation`, `places-discovery` (1 task each) | `infra/terraform/ecs-services` |
| Session table | `roam-travel-sessions` (on-demand, TTL `purgeAt`) | `infra/terraform/session-store` |

Created outside Terraform: the ECS cluster and Service Connect namespace (`ai-travel-app`), the execution role `ai-travel-app-ecs-execution`, the task role `ai-travel-app-ecs-task`, the security groups `ai-travel-app-session-sg`, `-recommendation-sg`, `-places-sg`, and the log groups `/ecs/ai-travel-companion-<service>` (14-day retention).

The services run in the default VPC public subnets with `assignPublicIp=ENABLED`, which avoids a NAT gateway. No security group accepts traffic from the internet: travel-session accepts 8080 only from the VPC link security group, and the internal services accept traffic only from the upstream service's security group.

travel-session is a Service Connect server (`discovery_name = "travel-session"`), so API Gateway finds its task in Cloud Map. Places Discovery runs with `ATTRACTION_PROVIDER=mock` and Recommendation with `TRAVEL_AI_ENABLED=false` until the Google and OpenAI keys are added. `CORS_ALLOWED_ORIGINS` is `http://localhost:5173`, so a deployed frontend needs its origin added.

The sections below were the original plan. Use them as a reference.

Region: `us-east-2`. Images come from the ECR repositories `ai-travel-companion-places-discovery`, `ai-travel-companion-recommendation`, and `ai-travel-companion-travel-session`, which the `ecr-push` job publishes on `master`.

## Topology

```
Internet ── ALB (public, :80/:443) ── travel-session (8080, public API)
                                          │  Service Connect: http://recommendation:8081
                                          ▼
                                     recommendation (8081, private)
                                          │  Service Connect: http://places-discovery:8082
                                          ▼
                                     places-discovery (8082, private) ── Google Places (HTTPS, optional)
recommendation ── OpenAI (HTTPS, optional)
```

- Only the Travel Session service is reachable from the internet. The standalone `/api/v1/recommendations` endpoint is served by the same service, so it stays public under the same ALB.
- Places Discovery and Recommendation are internal. Their security groups accept traffic only from other services in the cluster.
- Service-to-service calls use ECS Service Connect with the names in the table below. These match the defaults in `PLACES_DISCOVERY_BASE_URL` and `RECOMMENDATION_SERVICE_BASE_URL`.

| Service | Container port | Service Connect name | Public | Desired count | Notes |
|---|---|---|---|---|---|
| travel-session | 8080 | `travel-session` | Yes, via ALB | 1 | Sessions are in memory. Keep one task until a shared session store exists. |
| recommendation | 8081 | `recommendation` | No | 1 | Stateless. Can scale out. |
| places-discovery | 8082 | `places-discovery` | No | 1 | Stateless. Can scale out. |

## Files

- `infra/ecs/places-discovery.task-definition.json`
- `infra/ecs/recommendation.task-definition.json`
- `infra/ecs/travel-session.task-definition.json`

Each definition uses Fargate with 256 CPU and 512 MiB of memory, runs as UID 10001, and sends logs to CloudWatch (`/ecs/<family>`). Its container health check calls `/actuator/health`, and `stopTimeout` is 30 seconds, which lets the app's 25-second graceful shutdown finish after SIGTERM.

The files contain placeholders that must be replaced before registration:

| Placeholder | Replace with |
|---|---|
| `<ACCOUNT_ID>` | AWS account ID |
| `<IMAGE_TAG>` | Git commit SHA published by CI (the `ecr-push` job tags each image with `$GITHUB_SHA`) |
| `<SECRET_ARN_GOOGLE_PLACES_API_KEY>` | Secrets Manager ARN for the Google key. Only needed when `ATTRACTION_PROVIDER=google` |
| `<SECRET_ARN_OPENAI_API_KEY>` | Secrets Manager ARN for the OpenAI key. Only needed when `TRAVEL_AI_ENABLED=true` |
| `<FRONTEND_ORIGIN>` | Browser origin of the React app, for CORS |
| `<OKTA_ISSUER>` | Okta issuer URI, used by all three services (e.g. `https://<okta-domain>/oauth2/default`). Required |
| `<OKTA_AUDIENCE>` | Audience configured on the Okta authorization server, used by all three services. Required |

Keys are never placed in these files. ECS injects them from Secrets Manager at task start.

## Prerequisites (to create when deploying)

1. VPC with private subnets for the tasks and public subnets for the ALB. Keep Places Discovery and Recommendation off any public subnet route.
2. Security groups:
   - ALB: inbound 80/443 from the internet.
   - travel-session: inbound 8080 from the ALB security group only.
   - recommendation: inbound 8081 from the travel-session security group only.
   - places-discovery: inbound 8082 from the recommendation security group only.
   - All tasks: outbound 443 to the internet (ECR, Google Places, OpenAI).
3. ECS cluster with Service Connect enabled and a namespace (for example `ai-travel-companion`).
4. CloudWatch log groups `/ecs/ai-travel-companion-places-discovery`, `/ecs/ai-travel-companion-recommendation`, and `/ecs/ai-travel-companion-travel-session`.
5. Execution role `ai-travel-companion-ecs-execution` with `AmazonECSTaskExecutionRolePolicy` and `secretsmanager:GetSecretValue` on the two secret ARNs. The tasks need no task role for now.
6. Secrets Manager secrets for the Google and OpenAI keys, created only when those integrations are turned on.
7. An ALB with an HTTP listener and target group `travel-session` (protocol HTTP, port 8080, target type `ip`). Health check path `/actuator/health`, success codes `200`, interval 30 seconds, healthy threshold 2, unhealthy threshold 3.

The ALB health check path is `/actuator/health`. This is the only public actuator endpoint, and it reports `{"status":"UP"}` (HTTP 200) when the app can accept requests. Other actuator endpoints are not exposed.

## Deploy (commands to run when ready)

Register each definition after replacing the placeholders:

```bash
export AWS_REGION=us-east-2
export IMAGE_TAG=<commit-sha>
for svc in places-discovery recommendation travel-session; do
  sed -e "s/<ACCOUNT_ID>/<your-account-id>/g" -e "s/<IMAGE_TAG>/$IMAGE_TAG/g" \
    infra/ecs/$svc.task-definition.json > /tmp/$svc.td.json
  aws ecs register-task-definition --cli-input-json file:///tmp/$svc.td.json --region $AWS_REGION
done
```

Create or update each service. Use Service Connect for the internal services and attach the ALB target group to `travel-session`:

```bash
aws ecs create-service --cluster ai-travel-companion --service-name travel-session \
  --task-definition ai-travel-companion-travel-session --desired-count 1 --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[<private-subnets>],securityGroups=[<session-sg>],assignPublicIp=DISABLED}" \
  --load-balancers "targetGroupArn=<target-group-arn>,containerName=ai-travel-companion-travel-session,containerPort=8080" \
  --service-connect-configuration "enabled=true,namespace=ai-travel-companion"
```

Create the `recommendation` and `places-discovery` services the same way, without a load balancer. Give them `--service-connect-configuration` with a `services` entry that exposes port 8081 or 8082 under the names in the table above.

## Verification checklist (after deployment)

1. Each service reaches `RUNNING` with the health check `HEALTHY` in the ECS console.
2. The ALB target group reports the travel-session task as healthy.
3. `GET http://<alb-dns>/actuator/health` returns HTTP 200 with `{"status":"UP"}`.
4. `POST /api/v1/recommendations` through the ALB returns recommendations.
5. Session create, refresh, and delete work through the ALB. Scale-out is not safe until the session store is shared.
6. Places and Recommendation are not reachable from the internet.
7. CloudWatch shows no secret values in logs.

## Known limits

- Travel-session state is in memory, so it must stay at one task. Use a shared store before scaling it.
- `readonlyRootFilesystem` is not enabled. Enable it only after testing Tomcat's temp directory on a mounted volume.
- Autoscaling, HTTPS on the ALB, WAF, and log retention policies are not defined yet.
- The deployment steps are documented, not executed or tested against AWS.
