# Okta Local Setup and Troubleshooting

This guide covers configuring Okta for local development, getting an access token with Postman, running the backend services with Okta enabled, and fixing the errors we hit during setup.

## 1. Okta Admin Console

### 1.1 SPA application (`travel-app-frontend`)

Applications → Applications → `travel-app-frontend`

- Application type: Single-Page Application (SPA).
- Client authentication: None (public client). Do not create or store a client secret for the SPA.
- Proof Key for Code Exchange (PKCE): Required.
- Client ID: `YOUR_OKTA_CLIENT_ID`
- General → Sign-in redirect URIs:
  - `http://localhost:5173/login/callback` (React app)
  - `https://oauth.pstmn.io/v1/callback` (Postman)
- General → Sign-out redirect URIs: `http://localhost:5173`
- Trusted origins: `http://localhost:5173`
- Assignments: allow the users who should sign in.

### 1.2 Authorization server (`default`)

Security → API → Authorization Servers → `default`

- Issuer: `https://YOUR_OKTA_DOMAIN/oauth2/default`
- Audience: `api://travel-app`
- Scopes tab: `openid`, `profile`, and `email` are available by default. Add custom scopes such as `travel.read` only if you need per-permission checks.
- Access Policies: a policy must exist and be assigned to `travel-app-frontend`. Add one rule with:
  - Grant type: Authorization Code
  - Scopes requested: Any scopes
  - User is: Any user assigned the app

Without an access policy, Okta returns "Policies and rules are required for clients to access this resource".

## 2. Environment Variables

| Variable | Used by | Example |
|---|---|---|
| `OKTA_ISSUER` | All backend services | `https://YOUR_OKTA_DOMAIN/oauth2/default` |
| `OKTA_AUDIENCE` | All backend services | `api://travel-app` |
| `VITE_OKTA_ISSUER` | React frontend | `https://YOUR_OKTA_DOMAIN/oauth2/default` |
| `VITE_OKTA_CLIENT_ID` | React frontend | `YOUR_OKTA_CLIENT_ID` |
| `VITE_OKTA_REDIRECT_URI` | React frontend | `http://localhost:5173/login/callback` |
| `VITE_API_BASE_URL` | React frontend | `http://localhost:9080` |

Backend services need `OKTA_ISSUER` and `OKTA_AUDIENCE` at startup. If either is missing, startup fails with "OKTA_ISSUER must be set to the Okta issuer URI".

Never commit real secrets. The SPA Client ID is public, but still keep environment files out of Git.

## 3. Local Ports

| Service | Port | Notes |
|---|---|---|
| Places discovery | 8082 | Mock attraction provider by default |
| Recommendation | 8081 | Calls places discovery at `http://localhost:8082` |
| Travel session | 9080 | Port 8080 is used by Jenkins on this machine |
| React frontend | 5173 | Vite dev server |

Check for port conflicts first:

```bash
lsof -iTCP -sTCP:LISTEN -P | grep -E ":(8081|8082|9080|5173) "
```

## 4. Run the Backend

From the backend repository root, start each service in its own terminal.

```bash
export OKTA_ISSUER=https://YOUR_OKTA_DOMAIN/oauth2/default
export OKTA_AUDIENCE=api://travel-app
```

Places discovery:

```bash
cd services/places-discovery-service && ./mvnw spring-boot:run
```

Recommendation:

```bash
cd services/recommendation-service && PLACES_DISCOVERY_BASE_URL=http://localhost:8082 ./mvnw spring-boot:run
```

Travel session:

```bash
cd services/travel-session-service && SERVER_PORT=9080 ./mvnw spring-boot:run
```

Health checks:

```bash
curl -s localhost:8082/actuator/health
curl -s localhost:8081/actuator/health
curl -s localhost:9080/actuator/health
```

Each should return `{"status":"UP"}`.

### Attraction provider

- Default: `ATTRACTION_PROVIDER=mock`. Responses are sample data.
- For Google Places, set `ATTRACTION_PROVIDER=google` and `GOOGLE_PLACES_API_KEY` for the places discovery service. Do not commit the key.

## 5. Get an Access Token with Postman

Open the request → **Authorization** → Type **OAuth 2.0**.

| Field | Value |
|---|---|
| Grant Type | Authorization Code (With PKCE) |
| Code Challenge Method | SHA-256 |
| Callback URL | `https://oauth.pstmn.io/v1/callback` |
| Auth URL | `https://YOUR_OKTA_DOMAIN/oauth2/default/v1/authorize` |
| Access Token URL | `https://YOUR_OKTA_DOMAIN/oauth2/default/v1/token` |
| Client ID | `YOUR_OKTA_CLIENT_ID` |
| Client Secret | (empty) |
| Scope | `openid profile email` |
| Client Authentication | Send client credentials in body |

Click **Get New Access Token**, sign in with an Okta user, then click **Use Token**.

Do not put your email address in the Scope field. Do not set an audience parameter; the audience comes from the `default` authorization server.

## 6. Test the API

Replace `YOUR_TOKEN` with the access token.

Create a session:

```bash
curl -i -X POST http://localhost:9080/api/v1/travel-sessions \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"maxBudget":50,"interests":["ART"],"currencyCode":"USD","availableMinutes":120}'
```

Expected: `201` with `sessionId`, `status`, and `expiresAt`.

Refresh recommendations:

```bash
curl -i -X POST http://localhost:9080/api/v1/travel-sessions/SESSION_ID/recommendations \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"location":{"latitude":41.8902,"longitude":12.4922},"interests":["ART"],"maxBudget":50,"currencyCode":"USD","availableMinutes":120}'
```

End the session:

```bash
curl -i -X DELETE http://localhost:9080/api/v1/travel-sessions/SESSION_ID \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Expected: `204`.

Without a token, protected endpoints return `401`.

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Invalid 'state' parameter` | Sign-in was interrupted or retried in an old tab | Sign out of Okta, close old sign-in tabs, retry. Set Postman State to any value (for example `xyz123`). |
| `PKCE code challenge is required` | Postman grant type is not PKCE | Select Authorization Code (With PKCE) and SHA-256. |
| `invalid_scope` / scope not configured | Scope field is empty, wrong, or contains an email | Set Scope to `openid profile email`. |
| `Policies and rules are required` | No access policy on the authorization server | Add a policy assigned to `travel-app-frontend` and a rule (see 1.2). |
| Token request returns `401` with `Basic` | Postman sent the client ID as a Basic header for a public client | Set Client Authentication to "Send client credentials in body". |
| Token request host not found or `401` | Typo in host (`YOUR_OKTA_DOMAIN`) | Use `https://YOUR_OKTA_DOMAIN/oauth2/default/...`. |
| `Authentication window was closed` | Browser sign-in did not finish | Retry and keep the browser window open until Postman shows "Use Token". |
| `415 Unsupported Media Type` | Missing JSON content type | Add `Content-Type: application/json`. |
| `401` on API calls | Missing or expired token | Get a new token. Check `Authorization: Bearer ...` is present. |
| `503 Attraction provider unavailable` | Recommendation or places discovery is not running | Start both services with `OKTA_ISSUER` and `OKTA_AUDIENCE`. |
| Service fails with "OKTA_ISSUER must be set" | Environment variable not exported | Export `OKTA_ISSUER` and `OKTA_AUDIENCE` in the same terminal. |
| Port already in use | Another process holds the port | Run `lsof -iTCP -sTCP:LISTEN -P` and pick a free port, or stop the process. |

## 8. Production Notes

- Keep the SPA public client. Do not add a client secret to the React app.
- Access tokens are stored in sessionStorage. This is simpler than a backend-for-frontend (BFF) but readable by JavaScript if the app is compromised. A BFF with HttpOnly cookies is the stronger option for production.
- Add `offline_access` to the scope only if refresh tokens are required.
- Limit the Okta access policy rule to required scopes once custom scopes exist.
- Set `TRAVEL_REQUIRED_SCOPE` on the backend services when you introduce custom scopes.
- Use separate Okta applications and authorization servers for staging and production.

## 9. Quick Start for New Developers

Follow these steps to test the API locally from scratch. Mock mode needs no external paid API keys.

### 9.1 Prerequisites

- Java 21 (`java -version`)
- Git access to the backend repository
- Postman
- An Okta user assigned to the `travel-app-frontend` application. Ask the Okta admin to assign you.
- Ports 8081, 8082, and 9080 free (see section 3)

### 9.2 Start the services

From the backend repository root, export the Okta values in each terminal you use:

```bash
export OKTA_ISSUER=https://YOUR_OKTA_DOMAIN/oauth2/default
export OKTA_AUDIENCE=api://travel-app
```

Start the three services in this order, each in its own terminal:

```bash
# Terminal 1 - places discovery (mock provider)
cd services/places-discovery-service && ./mvnw spring-boot:run

# Terminal 2 - recommendation
cd services/recommendation-service && PLACES_DISCOVERY_BASE_URL=http://localhost:8082 ./mvnw spring-boot:run

# Terminal 3 - travel session
cd services/travel-session-service && SERVER_PORT=9080 ./mvnw spring-boot:run
```

Wait until each shows `Started ...Application`, then confirm:

```bash
curl -s localhost:8082/actuator/health
curl -s localhost:8081/actuator/health
curl -s localhost:9080/actuator/health
```

Each must return `{"status":"UP"}`.

### 9.3 Get a token

Follow section 5 to get an access token in Postman. Copy the token value, then store it in your terminal:

```bash
export TOKEN=paste_access_token_here
```

Tokens expire. If calls return `401`, repeat this step.

### 9.4 Call the API

1. Create a session. Copy `sessionId` from the response:

```bash
curl -s -X POST http://localhost:9080/api/v1/travel-sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"maxBudget":50,"interests":["ART"],"currencyCode":"USD","availableMinutes":120}'
```

2. Save the session ID for the next calls:

```bash
export SESSION_ID=paste_session_id_here
```

3. Refresh recommendations. Expect `200` with `"refreshStatus": "UPDATED"`:

```bash
curl -s -X POST http://localhost:9080/api/v1/travel-sessions/$SESSION_ID/recommendations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"location":{"latitude":41.8902,"longitude":12.4922},"interests":["ART"],"maxBudget":50,"currencyCode":"USD","availableMinutes":120}'
```

4. End the session. Expect `204` with no body:

```bash
curl -i -X DELETE http://localhost:9080/api/v1/travel-sessions/$SESSION_ID \
  -H "Authorization: Bearer $TOKEN"
```

5. Confirm that protected endpoints reject requests without a token. Expect `401`:

```bash
curl -i -X POST http://localhost:9080/api/v1/travel-sessions \
  -H "Content-Type: application/json" \
  -d '{}'
```

### 9.5 Expected data

- Recommendations in mock mode are sample data (for example, Trevi Fountain, Spanish Steps, Pantheon). They do not come from Google.
- To use Google Places, follow "Attraction provider" in section 4 and restart places discovery.

### 9.6 Stop the services

Press `Ctrl+C` in each terminal. If a port is still busy, see the "Port already in use" row in section 7.

### 9.7 Common first-time problems

- `503 Attraction provider unavailable`: places discovery or recommendation is not running, or started without `OKTA_ISSUER` and `OKTA_AUDIENCE`.
- `401` on every call: `TOKEN` is empty or expired. Check with `echo $TOKEN`.
- `415 Unsupported Media Type`: add `-H "Content-Type: application/json"`.
- `OKTA_ISSUER must be set`: the variable is not exported in that terminal.


## Mermaid diagram 
sequenceDiagram
actor User
participant Client as Postman / React app
participant Okta
participant ALB as AWS ALB
participant Session as travel-session :8080
participant Rec as recommendation :8081
participant Places as places-discovery :8082

    Note over User,Okta: 1. Get token (OAuth Authorization Code + PKCE)
    Client->>Okta: GET /oauth2/default/v1/authorize (scope: openid profile email, code_challenge)
    Okta->>User: Login page
    User->>Okta: Credentials
    Okta-->>Client: Redirect to callback with authorization code
    Client->>Okta: POST /oauth2/default/v1/token (code, code_verifier)
    Okta-->>Client: access_token (JWT, aud = api://travel-app)

    Note over Client,Places: 2. Create travel session
    Client->>ALB: POST /api/v1/travel-sessions (Bearer token)
    ALB->>Session: Forward request
    Session->>Okta: Fetch signing keys (cached), validate JWT iss/aud
    Session-->>Client: 201 {sessionId, status: ACTIVE, expiresAt}
    Note right of Session: Session stores JWT sub as owner (in memory)

    Note over Client,Places: 3. Refresh recommendations
    Client->>ALB: POST /api/v1/travel-sessions/{id}/recommendations (Bearer token)
    ALB->>Session: Forward request
    Session->>Session: Check session exists and belongs to sub
    Session->>Rec: POST (forwards Bearer token)
    Rec->>Okta: Validate JWT (cached keys)
    Rec->>Places: Search attractions (forwards Bearer token)
    Places->>Okta: Validate JWT (cached keys)
    Places-->>Rec: Places (mock or Google)
    Rec-->>Session: Recommendations
    Session-->>Client: 200 {refreshStatus: UPDATED, recommendations}

    Note over Client,Places: 4. End session
    Client->>ALB: DELETE /api/v1/travel-sessions/{id} (Bearer token)
    ALB->>Session: Forward request
    Session-->>Client: 204 No Content