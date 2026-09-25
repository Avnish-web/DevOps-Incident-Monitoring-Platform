# Operations

## Configuration reference

All configuration comes from environment variables (see `.env.example`). Secrets have no defaults,
and the services refuse to start without them.

| Variable | Service | Purpose |
|----------|---------|---------|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | api, worker | Database credentials (**secret**) |
| `DB_HOST`, `DB_PORT`, `DB_SSLMODE` | api, worker | Database location; `require` or `verify-full` where TLS is enforced |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` / `SPRING_DATA_REDIS_PASSWORD`, `REDIS_SSL` | api | Session store |
| `SESSION_REDIS_CONFIGURE_ACTION` | api | `none` on managed Redis that forbids `CONFIG` (set keyspace events there) |
| `ALERT_ENCRYPTION_KEY` | api, worker | AES-256 key for alert channel targets, **same value in both** (**secret**, `openssl rand -base64 32`) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | api | Bootstrap administrator, used only while no users exist (**secret**) |
| `SESSION_COOKIE_SECURE` | api | `true` behind HTTPS |
| `SESSION_TIMEOUT` | api | Idle session timeout (default `8h`) |
| `MAX_MONITORS_PER_USER`, `MAX_ALERT_CHANNELS_PER_USER` | api | Abuse limits (100 / 20) |
| `MONITORING_ALLOW_PRIVATE_TARGETS` | api, worker | **Disables SSRF protection**; only for deliberately internal setups |
| `WORKER_CONCURRENCY`, `WORKER_BATCH_SIZE`, `WORKER_POLL_INTERVAL`, `WORKER_SHUTDOWN_GRACE` | worker | Check execution tuning |
| `WORKER_HTTP_MAX_REDIRECTS`, `WORKER_HTTP_MAX_BODY` | worker | Per-check limits |
| `RETENTION_CHECK_RESULTS_DAYS` | worker | Raw history retention (default 30) |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `ALERT_EMAIL_FROM` | worker | SMTP for e-mail alerts; an empty host disables e-mail |
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` | grafana | Grafana login (**secret**) |

## Health and shutdown

| Endpoint | Port | Meaning |
|----------|------|---------|
| `/actuator/health/liveness` | api 8081, worker 8082 | Process is alive. The worker's liveness also fails if its polling loop stalls. |
| `/actuator/health/readiness` | api 8081, worker 8082 | Ready for traffic: database (and Redis for the API) reachable |
| `/actuator/prometheus` | api 8081, worker 8082 | Metrics |

Management ports are never proxied by Nginx. On SIGTERM, the worker stops claiming checks and
waits up to `WORKER_SHUTDOWN_GRACE` for running checks; the API drains in-flight requests.

## Logs

JSON (Elastic Common Schema) on stdout with a `requestId` on every line of a request, and
`monitorId` on every line of a check. Security events go to the `AUDIT` logger with structured
fields (`event.action`: `auth.login.success`, `auth.login.failure`, `auth.login.blocked`,
`auth.logout`, `auth.password.changed`, `admin.user.created`, `admin.user.deleted`, …). Filter them
with, for example, `docker compose logs api | grep '"logger":"AUDIT"'`.

## Metrics and alerts (Prometheus)

| Metric | Source | Meaning |
|--------|--------|---------|
| `monitoring_checks_total{outcome,error_type}` | worker | Completed checks |
| `monitoring_check_duration_seconds` | worker | Check duration histogram (SLO buckets) |
| `monitoring_scheduler_lag_seconds` | worker | How late checks start compared with their due time |
| `monitoring_checks_in_flight` | worker | Checks running now |
| `monitoring_incidents_total{event}` | worker | Incidents opened / resolved |
| `monitoring_alert_deliveries_total{channel_type,result}` | worker | Alert deliveries: sent, retry, failed, cancelled |
| `monitoring_monitors{state}` | api | Monitors by state (up, down, unknown, paused) |
| `monitoring_monitor_up{monitor_id,monitor_name}` | api | 1 = up, 0 = down |
| `monitoring_open_incidents` | api | Open incidents |
| `http_server_requests_seconds` | both | HTTP latency and status |

Every API replica reports the same fleet gauges, so aggregate them with `max`, not `sum`. Alert
rules (`infra/prometheus/rules/platform.yml`, unit-tested in `infra/prometheus/tests`) cover
API/worker down, no checks running, scheduler lag, API 5xx rate, targets down and a high
check-failure rate.

The **Grafana** dashboard *Monitoring Platform* (http://localhost:3000) is provisioned from
`infra/grafana` and read-only in the UI. It has three rows:

- **Fleet:** up/down, open incidents, success rate, per-monitor timeline
- **Checks:** throughput, errors by type, p50/p95 duration, scheduler lag, in flight, incidents
- **Services:** instances up, 5xx rate, requests, latency by endpoint, JVM heap, database connections

## Alerting

Incidents notify every enabled channel of the monitor's owner, through a transactional outbox:
the alert is stored in the same transaction as the incident, so it cannot be lost. The worker
delivers with retries (30 s … 1 h, 8 attempts). Delivery status and the last error are visible per
channel on the **Alerts** page.

## Scaling

- **API:** stateless (sessions in Redis), so run any number of replicas behind Nginx or a load balancer.
- **Worker:** run any number of replicas. `FOR UPDATE SKIP LOCKED` guarantees each check runs
  once, and a busy replica claims less. Scale out when `monitoring_scheduler_lag_seconds` p95
  stays above a few seconds, or raise `WORKER_CONCURRENCY`.
- **PostgreSQL:** the main table is `check_results`; retention bounds it (≈ 1 row per check).
  For very large fleets, add monthly partitioning (no API change needed).

## Backup and restore (Docker Compose)

```bash
# Backup
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > backup.dump
# Restore into an empty database (stop api and worker first)
docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < backup.dump
```

Back up `ALERT_ENCRYPTION_KEY` together with the database: without it, restored alert channels
cannot be decrypted. Redis holds only sessions and rate-limit counters, so it needs no backup. On
AWS, RDS takes automated daily backups with point-in-time recovery ([deployment.md](deployment.md)).
