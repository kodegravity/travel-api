# Web Push

Travelers can turn on notifications per browser on the Profile page. ROAM then alerts them about new places and
live events found by Travel Mode, even while the tab is in the background or the phone has paused it.

## How it works

```
Travel Mode refresh -> outbox -> SNS roam-travel-events -> SQS roam-notification-events
  -> NotificationEventListener (travel-session) -> session owner -> their push subscriptions
  -> encrypted POST to each browser's push service (FCM, Mozilla, Apple, WNS) -> service worker shows it
```

- `RecommendationsReady` becomes "N new places nearby", and each `NearbyEventDiscovered` event (at most two) becomes
  its own alert. They use the same notification tags as the page's own notifications (`travel-session-updates`,
  `live-event-<id>`), so a push and an in-page notification about the same thing replace each other.
- Messages are encrypted with RFC 8291 (`aes128gcm`) and signed with VAPID (RFC 8292), using only the JDK's crypto
  (`WebPushEncryption`, `Vapid`). `WebPushEncryptionTest` checks the key derivation against the RFC's worked example
  and decrypts the output from the browser's side.
- Only endpoints on known push services are accepted (`fcm.googleapis.com`, `*.push.services.mozilla.com`,
  `*.push.apple.com`, `*.notify.windows.com`, `android.googleapis.com`; HTTPS, default port), so a subscription
  cannot make the service call an arbitrary URL. Redirects are not followed.
- A 404 or 410 from a push service removes that subscription. Other failures are logged and not retried, so nobody
  is notified twice. Each traveler can have 5 browsers; the oldest is dropped past that.
- Subscriptions are stored in the sessions table under `push#<owner hash>` (see the README's "Account data").
- Push needs event messaging on (`TRAVEL_MESSAGING_ENABLED=true`), because the SQS consumer sends the pushes.
- The service worker always shows a notification for a push, as browsers require (Safari revokes subscriptions that
  stay silent). While ROAM is on screen it shows without sound.

Limits: pushes only come from Travel Mode refreshes, which the web page triggers as the traveler moves. If a phone
stops the page entirely, no refresh runs and nothing is sent. A server-side background check would close that gap.

On iPhone and iPad, Web Push works only for a site added to the Home Screen (iOS 16.4+). The Profile page explains
this.

## Keys

The VAPID key pair was generated on 2026-10-25:

```bash
openssl ecparam -name prime256v1 -genkey -noout -out key.pem
# public key: the 65-byte uncompressed point, base64url, into ecs-services/terraform.tfvars push_vapid_public_key
# private key: the 32-byte scalar, base64url, into SSM:
aws ssm put-parameter --name /roam/push/vapid-private-key --type SecureString --tier Standard --value file://private.txt
```

- The private key lives only in the SSM SecureString `/roam/push/vapid-private-key` (standard tier, AWS-managed key:
  free). It is created outside Terraform so it never enters state. `infra/terraform/secrets` lets the ECS execution
  role read it, and ECS injects it as `TRAVEL_PUSH_VAPID_PRIVATE_KEY`.
- The public key is not secret. It is in `terraform.tfvars` and the web app gets it from `GET /api/v1/push/config`.
- Changing the key pair invalidates every existing subscription. The web app notices a subscription made with a
  different key and subscribes again the next time the traveler turns notifications on.

## Settings

| Env var | Default | Notes |
|---|---|---|
| `TRAVEL_PUSH_ENABLED` | `false` | Off: `/push/config` says so and the Profile page hides the switch |
| `TRAVEL_PUSH_VAPID_PUBLIC_KEY` | none | base64url |
| `TRAVEL_PUSH_VAPID_PRIVATE_KEY` | none | base64url; from SSM on AWS |
| `TRAVEL_PUSH_SUBJECT` | `mailto:admin@localhost` | Contact for push services; on AWS the site URL |

With push on but a key missing or unreadable, the service still starts with push off and logs a warning.

## Cost

Nothing extra: push services are free, the SSM standard parameter is free, and the subscription items are a few
hundred bytes in the existing on-demand table.

## Checking it works

1. Profile → Notifications → "Turn on for this browser" (allow the permission prompt).
2. "Send a test" sends "Notifications are on" to every subscribed browser (at most once per 30 seconds).
3. Logs: `Push sent owner=... tag=... delivered=1` in `/ecs/ai-travel-companion-travel-session`; metric
   `travel.push.sent` by event type and outcome.
