# DevOps Incident & Monitoring Platform

A self-hosted platform that monitors websites and APIs, records response-time history,
detects incidents, exposes Prometheus metrics, and sends alerts.

> **Status:** Phase 4 — monitor CRUD REST API with SSRF-safe URL validation, on PostgreSQL.

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
| 5 | Monitoring worker | ⏳ |
| 6 | Health checks & response-time measurement | |
| 7 | Incident detection | |
| 8 | React dashboard | |
| 9 | Monitoring history & charts | |
| 10 | Prometheus metrics | |
| 11 | Grafana dashboards | |
| 12 | Alerting | |
| 13 | Authentication | |
| 14 | Docker Compose | |
| 15 | Nginx | |
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

## Running the API locally

```bash
cp .env.example .env              # then set a real POSTGRES_PASSWORD
docker compose up -d --wait       # PostgreSQL 17 on 127.0.0.1:${DB_PORT}

cd backend
./mvnw verify                     # build + tests (tests start their own PostgreSQL via Testcontainers)
./mvnw -pl api -am install -DskipTests
./mvnw -pl api spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile reads database settings from the root `.env`. Flyway migrates the
schema on startup. If a native PostgreSQL already uses port 5432, set `DB_PORT=5433` in `.env`.

| Port | Purpose |
|------|---------|
| 8080 | Public REST API (`/api/v1/...`) |
| 8081 | Internal management: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info` |

Logs are JSON (Elastic Common Schema) by default; the `local` profile switches to
readable text. Every response carries an `X-Request-Id` header, and errors use
RFC 9457 Problem Details (`application/problem+json`).

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/monitors` | Create a monitor → `201` + `Location` + `ETag` |
| `GET` | `/api/v1/monitors?page=0&size=20&sort=name,asc` | List (size ≤ 100; sort by `name`, `status`, `createdAt`, `updatedAt`) |
| `GET` | `/api/v1/monitors/{id}` | Get one → `ETag: "<version>"` |
| `PUT` | `/api/v1/monitors/{id}` | Replace configuration; optional `If-Match` → `412` if stale |
| `DELETE` | `/api/v1/monitors/{id}` | Delete monitor and its history → `204` |

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

> The API has no authentication until Phase 13 — do not expose it beyond localhost.

## Configuration

All configuration comes from environment variables. Copy the template and fill in your own values:

```bash
cp .env.example .env
```

`.env` is git-ignored. Never commit real credentials.

## License

MIT
