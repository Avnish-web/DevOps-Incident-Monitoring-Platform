# DevOps Incident & Monitoring Platform

A self-hosted platform that monitors websites and APIs, records response-time history,
detects incidents, exposes Prometheus metrics, and sends alerts.

> **Status:** Phase 14 — the full platform runs with one `docker compose up`: checks, incidents, history, alerting, auth, metrics and dashboards.

## Architecture at a glance

- **frontend**: React + TypeScript + Vite dashboard
- **api**: Spring Boot REST API (monitors, history, incidents, auth)
- **worker**: Spring Boot service that schedules and runs checks, detects incidents, and dispatches alerts
- **PostgreSQL**: system of record. **Redis**: alert event stream, cache, rate limits
- **Prometheus + Grafana**: metrics and dashboards. **Nginx**: reverse proxy and TLS

See [docs/architecture.md](docs/architecture.md) for the full design, including
the scheduling model, incident state machine, data model, and security principles.

## Roadmap

| # | Phase | Status |
|---|-------|--------|
| 1 | Project architecture | ✅ |
| 2 | Spring Boot backend | ✅ |
| 3 | PostgreSQL database | ✅ |
| 4 | Monitoring target CRUD | ✅ |
| 5 | Monitoring worker | ✅ |
| 6 | Health checks & response-time measurement | ✅ |
| 7 | Incident detection | ✅ |
| 8 | React dashboard | ✅ |
| 9 | Monitoring history & charts | ✅ |
| 10 | Prometheus metrics | ✅ |
| 11 | Grafana dashboards | ✅ |
| 12 | Alerting | ✅ |
| 13 | Authentication | ✅ |
| 14 | Docker Compose | ✅ |
| 15 | Nginx | ⏳ |
| 16 | GitHub Actions CI/CD | |
| 17 | AWS deployment | |
| 18 | Terraform | |
| 19 | Security hardening | |
| 20 | Documentation & production-readiness review | |

## Prerequisites

- JDK 17+
- Node.js 20+
- Docker with Docker Compose v2
- Git

## Quick start (Docker Compose)

```bash
cp .env.example .env     # then set every secret: POSTGRES_PASSWORD, REDIS_PASSWORD,
                         # ALERT_ENCRYPTION_KEY (openssl rand -base64 32), ADMIN_EMAIL,
                         # ADMIN_PASSWORD, GRAFANA_ADMIN_PASSWORD
docker compose up -d --build
```

| URL | What |
|-----|------|
| http://localhost:8080 | Dashboard and API (`/api`), behind Nginx; sign in with `ADMIN_EMAIL` / `ADMIN_PASSWORD` |
| http://localhost:3000 | Grafana (`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`) |
| http://localhost:9090 | Prometheus |

The stack runs PostgreSQL, Redis, the API, the worker, Nginx with the dashboard, Prometheus and
Grafana. It is hardened as follows:

- **Network isolation:** PostgreSQL and Redis sit on an `internal` network (unreachable from the
  host, no internet access). Only the worker and API get outbound access, for checks, alerts and
  DNS validation.
- **Containers:** every application container runs as a non-root user with a read-only root
  filesystem, all capabilities dropped, `no-new-privileges`, memory/CPU/PID limits and rotated logs.
- **Startup order:** gated on health. The API (which migrates the schema) must be ready before
  the worker and Nginx start. `docker compose stop` sends SIGTERM for graceful shutdown.
- **Ports:** all published ports are bound to `127.0.0.1`.

To run the services from an IDE instead, start only the databases with the development overlay,
which publishes them on localhost:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres redis
```

## Running the API locally

```bash
cp .env.example .env              # then set the secrets (see Quick start)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --wait postgres redis

cd backend
./mvnw verify                     # build + tests (tests start their own PostgreSQL via Testcontainers)
./mvnw -pl api -am install -DskipTests
./mvnw -pl api spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile reads database settings from the root `.env`. Flyway migrates the
schema on startup. If a native PostgreSQL already uses port 5432, set `DB_PORT=5433` in `.env`.

### Running the worker

Start the API first: it owns schema migrations. Then, in another terminal:

```bash
cd backend
./mvnw -pl worker -am install -DskipTests
./mvnw -pl worker spring-boot:run -Dspring-boot.run.profiles=local
curl localhost:8082/actuator/health/readiness
```

The worker claims due monitors from PostgreSQL (`FOR UPDATE SKIP LOCKED`), so any number
of instances can run side by side; give each its own `WORKER_MANAGEMENT_PORT`. Each check
is a real HTTP(S) request. Its status, latency and classified error (`TIMEOUT`,
`DNS_FAILURE`, `CONNECTION_REFUSED`, `TLS_ERROR`, `UNEXPECTED_STATUS`, `BLOCKED_TARGET`, …)
are stored in `check_results`:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT checked_at, success, status_code, latency_ms, error_type FROM check_results ORDER BY checked_at DESC LIMIT 10"'
```

| Port | Purpose |
|------|---------|
| 8080 | Public REST API (`/api/v1/...`) |
| 8081 | Internal management: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info` |

Logs are JSON (Elastic Common Schema) by default; the `local` profile switches to
readable text. Every response carries an `X-Request-Id` header, and errors use
RFC 9457 Problem Details (`application/problem+json`).

### Running the dashboard

```bash
cd frontend
npm ci
npm run dev          # http://localhost:5173, proxies /api to the API on :8080
npm test             # unit/component tests (Vitest)
npm run lint && npm run build
```

The dashboard lists monitors with live status (refreshing every 15 s) and supports create,
edit, pause/resume and delete. It also shows monitor details and incident history. Form
validation mirrors the API's rules, and server-side field errors (such as a blocked private
address) appear next to the field. Edits send `If-Match`, so a concurrent change is reported
instead of overwritten.

### Metrics (Prometheus)

Both services expose `/actuator/prometheus` on their **internal** management port (API 8081,
worker 8082). Nginx never proxies this port. `docker compose up -d prometheus` starts
Prometheus on http://localhost:9090 with the scrape config and alert rules in
`infra/prometheus/`.

| Metric | Source | Meaning |
|--------|--------|---------|
| `monitoring_checks_total{outcome,error_type}` | worker | Completed checks |
| `monitoring_check_duration_seconds` | worker | Check duration histogram (SLO buckets) |
| `monitoring_scheduler_lag_seconds` | worker | How late checks start vs. their due time |
| `monitoring_checks_in_flight` | worker | Checks running now |
| `monitoring_incidents_total{event}` | worker | Incidents opened / resolved |
| `monitoring_monitors{state}` | API | Monitors by state (up, down, unknown, paused) |
| `monitoring_monitor_up{monitor_id,monitor_name}` | API | 1 = up, 0 = down per monitor |
| `monitoring_open_incidents` | API | Open incidents |
| `http_server_requests_seconds` | both | HTTP latency / status (Spring Boot) |

Alert rules (`infra/prometheus/rules/platform.yml`) cover API/worker down, no checks running,
scheduler lag, API 5xx rate, targets down and high check-failure rate. Validate and test them:

```bash
docker run --rm --entrypoint promtool -v "$PWD/infra/prometheus:/etc/prometheus:ro"   prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml
docker run --rm --entrypoint promtool -v "$PWD/infra/prometheus:/etc/prometheus:ro"   prom/prometheus:v3.14.0 test rules /etc/prometheus/tests/platform_test.yml
```

### Dashboards (Grafana)

`docker compose up -d grafana` starts Grafana on http://localhost:3000. Log in with
`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` from `.env`; sign-up and anonymous access
are disabled. The Prometheus datasource and the **Monitoring Platform** dashboard are
provisioned from `infra/grafana/` (read-only in the UI, so edit the JSON in the repository):

- **Fleet:** monitors up/down, open incidents, check success rate, per-monitor status timeline
- **Checks:** checks/s by outcome, failures by error type, p50/p95 check duration, scheduler lag, checks in flight, incidents
- **Services:** API/worker instances up, API 5xx rate, requests by status, p95 latency by endpoint, JVM heap, DB connections

### Alerting

Every enabled alert channel is notified when a monitor goes down and when it recovers. You
manage channels on the **Alerts** page of the dashboard or through the API.

- **E-mail:** the worker needs SMTP settings (`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, optionally
  `SPRING_MAIL_USERNAME`/`SPRING_MAIL_PASSWORD`, and `ALERT_EMAIL_FROM`). For local development,
  `docker compose --profile dev up -d mailpit` catches mail at http://localhost:8025.
- **Slack:** paste an incoming-webhook URL (`https://hooks.slack.com/services/...`).
- **Webhook:** receives a JSON payload with an `X-Monitoring-Signature: sha256=<hex>` HMAC over
  `X-Monitoring-Timestamp + "." + body`, using the secret shown once at creation.

Channel targets are encrypted at rest with `ALERT_ENCRYPTION_KEY` (`openssl rand -base64 32`,
same value for API and worker). Keep it safe: without it, stored channels cannot be decrypted.

### Authentication

- **Sessions:** server-side sessions live in **Redis** (`docker compose up -d redis`), behind an
  HttpOnly, `SameSite=Lax` cookie (`Secure` when `SESSION_COOKIE_SECURE=true`). JavaScript never
  sees a credential, logout takes effect immediately, and any API replica can serve any user.
- **CSRF:** the cookie-to-header pattern. `GET /api/v1/auth/csrf` issues the `XSRF-TOKEN` cookie,
  and every state-changing request must echo it in `X-XSRF-TOKEN`. The token is rotated at login.
- **Accounts:** the first administrator comes from `ADMIN_EMAIL` / `ADMIN_PASSWORD` when no users
  exist; remove those variables afterwards. Administrators add users on the **Users** page.
  Passwords must be 12–64 characters and are stored as BCrypt hashes.
- **Brute force:** 5 failed logins per account or 30 per IP within 15 minutes → `429` with
  `Retry-After`. The same `401` message is returned for an unknown user and a wrong password.
- **Isolation:** each user sees only their own monitors, incidents, history and alert channels
  (other users' resources return `404`), and alerts go only to the owner's channels. Deleting a
  user ends their sessions immediately.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/auth/csrf` | Issue the CSRF cookie (`204`) |
| `POST` | `/api/v1/auth/login` | `{email, password}` → current user, sets the session cookie |
| `POST` | `/api/v1/auth/logout` | End the session (`204`) |
| `GET` | `/api/v1/auth/me` | Current user |
| `POST` | `/api/v1/auth/password` | `{currentPassword, newPassword}` |
| `GET` / `POST` / `DELETE` | `/api/v1/users[/{id}]` | User administration (ADMIN only) |

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/monitors` | Create a monitor → `201` + `Location` + `ETag` |
| `GET` | `/api/v1/monitors?page=0&size=20&sort=name,asc` | List (size ≤ 100; sort by `name`, `status`, `createdAt`, `updatedAt`) |
| `GET` | `/api/v1/monitors/{id}` | Get one → `ETag: "<version>"` |
| `PUT` | `/api/v1/monitors/{id}` | Replace configuration; optional `If-Match` → `412` if stale |
| `DELETE` | `/api/v1/monitors/{id}` | Delete monitor and its history → `204` |
| `GET` | `/api/v1/monitors/{id}/checks?page&size` | Raw check results, newest first |
| `GET` | `/api/v1/monitors/{id}/stats?range=1h\|24h\|7d\|30d` | Uptime %, avg/p50/p95/max latency and a bucketed time series |
| `GET` | `/api/v1/incidents?status=OPEN\|RESOLVED&monitorId=…&page&size` | Incidents, newest first |
| `GET` | `/api/v1/incidents/{id}` | One incident |
| `POST` | `/api/v1/alert-channels` | Add an e-mail, Slack or webhook channel (webhooks return a signing secret once) |
| `GET` / `PUT` / `DELETE` | `/api/v1/alert-channels[/{id}]` | List, update (target optional), delete |
| `POST` | `/api/v1/alert-channels/{id}/test` | Queue a test notification → `202` |
| `GET` | `/api/v1/alert-channels/{id}/deliveries` | Delivery history (status, attempts, last error) |

A monitor goes `DOWN` and opens an incident after `failureThreshold` consecutive failed
checks, and returns to `UP` (resolving the incident) after `recoveryThreshold` consecutive
successes. Incidents report `startedAt` (first failure), `resolvedAt` (first success),
`durationSeconds`, `cause`, and a `resolution` of `RECOVERED`, `MONITOR_PAUSED` or
`MONITOR_CHANGED`.

```bash
curl -X POST localhost:8080/api/v1/monitors -H "Content-Type: application/json" \
  -d '{"name":"Example","url":"https://example.com","intervalSeconds":60}'
```

Fields: `name` (required, ≤ 100), `url` (required, http/https), `httpMethod` (`GET`|`HEAD`),
`intervalSeconds` (30–86400, default 60), `timeoutMs` (1000–30000, default 5000),
`expectedStatus` (100–599, default any 2xx/3xx), `failureThreshold` / `recoveryThreshold`
(1–10, defaults 3 / 2), `enabled` (default true).

URLs pointing at loopback, private, link-local (e.g. cloud metadata `169.254.169.254`) or other
reserved addresses are rejected, including host names that resolve to them.

All `/api/v1/**` endpoints require a session (see **Authentication** below). Mutating requests
also need the `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie.

## Configuration

All configuration comes from environment variables. Copy the template and fill in your own values:

```bash
cp .env.example .env
```

`.env` is git-ignored. Never commit real credentials.

## License

MIT
