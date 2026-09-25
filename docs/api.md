# REST API

Base path `/api/v1`, JSON only. All endpoints except `GET /auth/csrf` and `POST /auth/login`
require a session cookie. State-changing requests (POST/PUT/DELETE) also need the
`X-XSRF-TOKEN` header, whose value must match the `XSRF-TOKEN` cookie.

Errors use [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457)
(`application/problem+json`) with a `requestId` (also in the `X-Request-Id` header). Validation
errors list the failing fields:

```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "errors": [{ "field": "url", "message": "URL points to a private, loopback or reserved address, which is not allowed" }],
  "requestId": "3f1c…"
}
```

| Status | Meaning |
|--------|---------|
| 400 | Validation failed (see `errors`), malformed JSON, unknown JSON field |
| 401 | Not signed in, or wrong credentials |
| 403 | Missing/invalid CSRF token, or not an administrator |
| 404 | Not found, including another user's resource |
| 409 | Concurrent modification, or a per-user limit reached |
| 412 | `If-Match` does not match the current version |
| 429 | Rate limited (`Retry-After`) |
| 503 | A dependency (session store) is temporarily unavailable (`Retry-After`) |

## Authentication

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/auth/csrf` | Issues the `XSRF-TOKEN` cookie → `204` |
| `POST` | `/auth/login` | `{email, password}` → `{id, email, role}`, sets the session cookie. The CSRF token is rotated, so fetch a new one afterwards. |
| `POST` | `/auth/logout` | Ends the session → `204` |
| `GET` | `/auth/me` | Current user |
| `POST` | `/auth/password` | `{currentPassword, newPassword}` → `204` |

```bash
# Scripted access with curl (cookie jar):
curl -c jar -b jar -s localhost:8080/api/v1/auth/csrf
X=$(awk '/XSRF-TOKEN/ {print $7}' jar)
curl -c jar -b jar -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"…"}' localhost:8080/api/v1/auth/login
curl -c jar -b jar -s localhost:8080/api/v1/auth/csrf; X=$(awk '/XSRF-TOKEN/ {print $7}' jar)
curl -b jar -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"name":"Example","url":"https://example.com"}' localhost:8080/api/v1/monitors
```

## Monitors

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/monitors` | Create → `201`, `Location`, `ETag: "0"` |
| `GET` | `/monitors?page=0&size=20&sort=name,asc` | List own monitors (size ≤ 100; sort by `name`, `status`, `createdAt`, `updatedAt`) |
| `GET` | `/monitors/{id}` | One monitor, `ETag: "<version>"` |
| `PUT` | `/monitors/{id}` | Replace the configuration; optional `If-Match: "<version>"` |
| `DELETE` | `/monitors/{id}` | Delete with its history → `204` |

| Field | Rules | Default |
|-------|-------|---------|
| `name` | required, ≤ 100 characters, no control characters | |
| `url` | required, `http`/`https`, no credentials, must not resolve to private/loopback/link-local/reserved addresses | |
| `httpMethod` | `GET` or `HEAD` | `GET` |
| `intervalSeconds` | 30 – 86400 | 60 |
| `timeoutMs` | 1000 – 30000 | 5000 |
| `expectedStatus` | 100 – 599, or null for any 2xx/3xx | null |
| `failureThreshold` | 1 – 10 consecutive failures before DOWN | 3 |
| `recoveryThreshold` | 1 – 10 consecutive successes before UP | 2 |
| `enabled` | pause (`false`) / resume | true |

Read-only fields in responses: `id`, `type`, `status` (`UNKNOWN`, `UP`, `DOWN`), `lastCheckedAt`,
`nextCheckAt`, `createdAt`, `updatedAt`, `version`.

Pausing a monitor, or changing its URL, method, timeout or expected status, closes an open
incident (`MONITOR_PAUSED` / `MONITOR_CHANGED`) and resets the status to `UNKNOWN`. Each user can
have at most 100 monitors (`MAX_MONITORS_PER_USER`).

## History

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/monitors/{id}/checks?page&size` | Raw results, newest first: `checkedAt`, `success`, `statusCode`, `latencyMs`, `errorType`, `errorMessage` |
| `GET` | `/monitors/{id}/stats?range=1h\|24h\|7d\|30d` | `summary` (checks, failures, uptime %, avg/p50/p95/max latency) and `series` of fixed buckets |

Error types: `TIMEOUT`, `DNS_FAILURE`, `CONNECTION_REFUSED`, `TLS_ERROR`, `UNEXPECTED_STATUS`,
`BLOCKED_TARGET`, `BODY_MISMATCH` (reserved), `OTHER`. Latency statistics cover successful checks
only; buckets without checks have null latency, which charts show as gaps.

## Incidents

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/incidents?status=OPEN\|RESOLVED&monitorId=&page&size` | Newest first |
| `GET` | `/incidents/{id}` | One incident |

Fields: `monitorId`, `monitorName`, `status`, `startedAt` (first failing check), `resolvedAt`
(first successful check), `durationSeconds`, `cause`, and `resolution` (`RECOVERED`,
`MONITOR_PAUSED`, `MONITOR_CHANGED`).

## Alert channels

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/alert-channels` | `{name, type, target, enabled}`. Webhook channels return `signingSecret` **once**. |
| `GET` | `/alert-channels` | Own channels with a masked `targetPreview` |
| `GET` / `PUT` / `DELETE` | `/alert-channels/{id}` | On `PUT`, omit `target` to keep the stored value; the type cannot change |
| `POST` | `/alert-channels/{id}/test` | Queue a test notification → `202` |
| `GET` | `/alert-channels/{id}/deliveries` | Delivery history: `eventType`, `status` (`PENDING`, `SENT`, `FAILED`, `CANCELLED`), `attempts`, `lastError` |

| Type | Target |
|------|--------|
| `EMAIL` | An e-mail address (the worker needs SMTP settings) |
| `SLACK` | `https://hooks.slack.com/services/…` incoming webhook |
| `WEBHOOK` | An HTTPS URL receiving the payload below |

Webhook requests carry `X-Monitoring-Event`, `X-Monitoring-Delivery` (use it for de-duplication;
delivery is at-least-once), `X-Monitoring-Timestamp` and
`X-Monitoring-Signature: sha256=<hex HMAC-SHA256(secret, timestamp + "." + body)>`:

```json
{
  "event": "INCIDENT_RESOLVED",
  "occurredAt": "2030-01-01T00:01:00Z",
  "monitor": { "id": "…", "name": "Shop", "url": "https://shop.example.com" },
  "incident": { "id": "…", "startedAt": "2030-01-01T00:00:00Z", "resolvedAt": "2030-01-01T00:01:00Z",
                "cause": "TIMEOUT: No complete response within 5000 ms", "durationSeconds": 60 }
}
```

Verify signatures in constant time and reject timestamps older than a few minutes (replay
protection). Each user can have at most 20 channels (`MAX_ALERT_CHANNELS_PER_USER`).

## Users (administrators only)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/users` | All users (never includes password hashes) |
| `POST` | `/users` | `{email, password, role}` (`ADMIN` or `USER`); password 12–64 characters |
| `DELETE` | `/users/{id}` | Deletes the user with their monitors and channels, and ends their sessions; you cannot delete yourself |
