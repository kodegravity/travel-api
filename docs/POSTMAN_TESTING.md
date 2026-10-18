# Postman Testing Guide

This guide covers manual API testing for the three backend services in `services/`. Use the sample session ID below for the refresh request, or replace it with a session you created.

## 1. Environment Setup

Create a Postman environment with these variables:

| Variable | Sample value | Notes |
|---|---|---|
| `baseUrl` | `http://ai-travel-app-alb-179383308.us-east-2.elb.amazonaws.com` | Public API (travel-session-service). |
| `accessToken` | (paste value) | Okta access token. Never commit it. |
| `sessionId` | `32a47ac1-5746-431b-bb81-b4a668a876cd` | Sample. Replace with the `sessionId` from the create response. |

**Authorization:** For every `/api/v1/**` request, set Authorization to **Bearer Token** with `{{accessToken}}`. Requests without a valid token return `401`. A session owned by another user returns `404`.

**Content-Type:** Set `application/json` on every request that has a body.

**Internal services:** `/internal/v1/**` requires the same Okta Bearer token as the public API (`{{accessToken}}`). Routing through the ALB is required before these work from outside; see `docs/ECS_DEPLOYMENT.md` for the listener and target group setup.

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
- **Sample URL:** `http://ai-travel-app-alb-179383308.us-east-2.elb.amazonaws.com/api/v1/travel-sessions/32a47ac1-5746-431b-bb81-b4a668a876cd/recommendations`
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

Called by travel-session-service. Routed through the ALB only if `/internal/**` is added.

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

Called by recommendation-service. Routed through the ALB only if `/internal/**` is added.

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
- The ALB host above is taken from the request provided. Whether the ALB routes to all three services is UNVERIFIED.
- Internal endpoint requests were written from the controller and DTO source. They were not sent against a running service in this document.
