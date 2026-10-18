# Postman Testing Guide

Manual API testing against the deployed stack. The only public entry point is API Gateway (HTTPS). recommendation-service and places-discovery-service have no public route; test them locally (sections 3 and 4 with `localhost` URLs).

## 1. Setup

**Import** (Postman → Import) both files from `docs/postman/`:

- `roam-ai.postman_collection.json`: 8 requests with test scripts, run in order (0 → 7).
- `roam-ai-aws.postman_environment.json`: `baseUrl` = `https://t1mmzzwy99.execute-api.us-east-2.amazonaws.com`, `accessToken` empty.

Select the **ROAM / AI – AWS (API Gateway)** environment.

**Get an Okta access token** (expires after about 1 hour):

- **Option A, from the web app:** log in at `http://localhost:5173`, open DevTools → Application → Session Storage → `http://localhost:5173` → `okta-token-storage`, and copy `accessToken.accessToken` (a long string starting with `eyJ`). Paste it into the environment's `accessToken` (current value).
- **Option B, in Postman:** collection → Authorization → OAuth 2.0, grant type *Authorization Code (With PKCE)*, Auth URL `<OKTA_ISSUER>/v1/authorize`, Access Token URL `<OKTA_ISSUER>/v1/token`, the SPA client ID, scope `openid profile email`, *Authorize using browser*. Add `https://oauth.pstmn.io/v1/callback` to the Okta app's sign-in redirect URIs first.

The token's `aud` must equal the API's `OKTA_AUDIENCE`, or API Gateway returns `401`.

**Run:** collection → Run → keep the order. Every request should pass its tests. Each response carries `X-Request-Id`, which links the request across the logs of all three services in Datadog.

| Variable | Notes |
|---|---|
| `baseUrl` | API Gateway URL (environment) |
| `accessToken` | Okta access token. Keep it as a secret variable and never commit or export it. |
| `sessionId` | Set automatically by request 2 (collection variable) |

**Authorization:** every `/api/v1/**` request uses Bearer `{{accessToken}}` (set on the collection). Requests without a valid token get `401` from API Gateway. API Gateway's `401` has no CORS headers, so a browser reports it as a CORS error. A session owned by another user returns `404`.

**Content-Type:** `application/json` on every request that has a body.

**Rate limit:** API Gateway allows 5 requests per second (burst 10). Faster runs get `429`.

## 2. Public API (travel-session-service)

### 2.1 Create a travel session

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/v1/travel-sessions`
- **Auth:** Bearer `{{accessToken}}`

Body:

```json
{
  "interests": ["HISTORY", "FOOD", "ART"],
  "maxBudget": 50,
  "currencyCode": "EUR",
  "availableMinutes": 180
}
```

Validation:
- `interests`: non-empty. Allowed values are `HISTORY`, `FOOD`, `ART`, `NATURE`, `SHOPPING`, `LOCAL_EXPERIENCES`.
- `maxBudget`: zero or positive.
- `currencyCode`: valid ISO code.
- `availableMinutes`: 15 to 720.

Expected: `201 Created` with a `Location` header and body `{ "sessionId", "status": "ACTIVE", "expiresAt" }`. Copy `sessionId` into the environment.

Postman test script (optional):

```javascript
pm.test("Session created", () => {
  pm.response.to.have.status(201);
  pm.environment.set("sessionId", pm.response.json().sessionId);
});
```

### 2.2 Refresh session recommendations (Active Travel Mode)

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/v1/travel-sessions/{{sessionId}}/recommendations`
- **Sample URL:** `{{baseUrl}}/api/v1/travel-sessions/32a47ac1-5746-431b-bb81-b4a668a876cd/recommendations`
- **Auth:** Bearer `{{accessToken}}`

Body:

```json
{
  "location": {
    "latitude": 41.9028,
    "longitude": 12.4964
  }
}
```

Expected: `200 OK` with body `{ "sessionId", "refreshStatus", "recommendations", "generatedAt" }`.

`refreshStatus` values:
- `UPDATED`: new places returned.
- `UNCHANGED`: the traveler has not moved 250 m, or the 5-minute cooldown has not passed.
- `NO_NEW_RESULTS`: no new eligible places.

Repeated calls are throttled. Calling again within 5 minutes or without moving returns `UNCHANGED`.

### 2.3 Stop a travel session

- **Method:** `DELETE`
- **URL:** `{{baseUrl}}/api/v1/travel-sessions/{{sessionId}}`
- **Auth:** Bearer `{{accessToken}}`
- **Body:** none

Expected: `204 No Content`. Stopping an already-stopped session is idempotent. Stopping an expired session returns `410 Gone`.

### 2.4 One-off recommendations (no session)

- **Method:** `POST`
- **URL:** `{{baseUrl}}/api/v1/recommendations`
- **Auth:** Bearer `{{accessToken}}`

Body:

```json
{
  "location": {
    "latitude": 41.9028,
    "longitude": 12.4964
  },
  "interests": ["HISTORY", "NATURE"],
  "maxBudget": 40,
  "currencyCode": "EUR",
  "availableMinutes": 120
}
```

Expected: `200 OK` with `{ "recommendations": [...] }`.

## 3. Internal API: Recommendation Service

Called by travel-session-service. Not exposed publicly; run the service locally to test it directly.

### 3.1 Search recommendations

- **Method:** `POST`
- **URL:** `{{baseUrl}}/internal/v1/recommendations`
- **Auth:** Bearer `{{accessToken}}`

Body:

```json
{
  "request": {
    "location": {
      "latitude": 41.9028,
      "longitude": 12.4964
    },
    "interests": ["HISTORY", "FOOD"],
    "maxBudget": 50,
    "currencyCode": "EUR",
    "availableMinutes": 180
  },
  "excludedPlaceIds": ["already-seen-place-id"]
}
```

Expected: `200 OK` with `{ "recommendations": [...] }`. `excludedPlaceIds` is optional.

## 4. Internal API: Places Discovery Service

Called by recommendation-service. Not exposed publicly; run the service locally to test it directly.

### 4.1 Search nearby attractions

- **Method:** `POST`
- **URL:** `{{baseUrl}}/internal/v1/places/search`
- **Auth:** Bearer `{{accessToken}}`

Body:

```json
{
  "location": {
    "latitude": 41.9028,
    "longitude": 12.4964
  },
  "searchRadiusKm": 5.0
}
```

Validation: `searchRadiusKm` is between 0.1 and 50.0.

Expected: `200 OK` with `{ "attractions": [...] }`. An empty list means no results.

## 5. Error Cases to Test

All errors use RFC 9457 `application/problem+json`.

| Scenario | Expected status |
|---|---|
| No or invalid token | `401` |
| Session owned by another user, or unknown ID | `404` |
| Session expired | `410` |
| Invalid body (for example, `availableMinutes: 5`) | `400` with `errors` |
| Malformed JSON | `400` |
| Recommendation or Places service down | `503` |
| Refresh already in progress | `409` |

## 6. Checklist

1. Create a session and save `sessionId`.
2. Refresh with a location. Expect `UPDATED` or `NO_NEW_RESULTS`.
3. Refresh again immediately. Expect `UNCHANGED`.
4. Stop the session. Expect `204`.
5. Refresh the stopped session. Expect `404` or `410`.
6. Run the one-off recommendation request.
7. Run the internal requests against local services.

## Notes

- Do not put real tokens in exported Postman collections.
- Internal endpoint requests were written from the controller and DTO source. They were not sent against a running service in this document.
