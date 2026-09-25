# Development

## Prerequisites

- JDK 17+ (Temurin recommended)
- Node.js 24
- Docker with Compose v2 (for the stack and for Testcontainers)
- Git

## Everything in containers

```bash
cp .env.example .env        # set the secrets, see the README quick start
docker compose up -d --build
```

## Services from the IDE

Start only PostgreSQL and Redis. The development overlay publishes them on localhost:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --wait postgres redis

cd backend
./mvnw -pl api -am install -DskipTests
./mvnw -pl api spring-boot:run -Dspring-boot.run.profiles=local       # API :8080, management :8081
./mvnw -pl worker spring-boot:run -Dspring-boot.run.profiles=local    # worker management :8082

cd frontend
npm ci && npm run dev       # http://localhost:5173, proxies /api to :8080
```

The `local` profile reads the root `.env`, switches to human-readable logs, and connects to
the databases on localhost. Start the API before the worker on a fresh database: only the API runs
Flyway migrations. If a native PostgreSQL already uses port 5432, set `DB_PORT=5433` in `.env`.

To monitor services on your own machine or LAN during development, set
`MONITORING_ALLOW_PRIVATE_TARGETS=true`. That disables SSRF protection, so never enable it on a
shared deployment.

E-mail alerts locally: `docker compose --profile dev up -d mailpit`, then set
`SPRING_MAIL_HOST=localhost` and `SPRING_MAIL_PORT=1025` and open http://localhost:8025.

## Tests

```bash
cd backend && ./mvnw verify     # unit + integration tests; Testcontainers starts PostgreSQL and Redis
cd frontend && npm test && npm run lint && npm run typecheck
```

| Suite | What it covers |
|-------|----------------|
| `common` | Address blocklist and URL validation (SSRF), AES-GCM secrets, JPA mappings against the Flyway schema, database constraints |
| `api` | Every endpoint over real HTTP with real PostgreSQL and Redis: auth, CSRF, lockout, user isolation, quotas, audit log, validation, ETags, stats math, metrics |
| `worker` | Concurrent claiming (no duplicates), backpressure, graceful and forced shutdown, HTTP checks against a local server (timeouts, TLS, redirects, SSRF at connect time), incident state machine, alert outbox, signing, retries, retention |
| `frontend` | Form validation, API client (CSRF, retries, Problem Details), components |

Infrastructure checks (also run in CI):

```bash
docker run --rm --entrypoint promtool -v "$PWD/infra/prometheus:/etc/prometheus:ro" \
  prom/prometheus:v3.14.0 test rules /etc/prometheus/tests/platform_test.yml
docker run --rm -v "$PWD/deploy/terraform:/tf" hashicorp/terraform:1.16.4 -chdir=/tf fmt -recursive -check
```

## Conventions

- **Backend:** one Maven multi-module build (`common`, `api`, `worker`). Schema changes are new
  Flyway migrations in `common/src/main/resources/db/migration`, and existing migrations are never
  edited. Errors are Problem Details. Every query is scoped to the current user.
- **Frontend:** React function components, TanStack Query for server state, and no raw HTML
  rendering (lint rule).
- **Commits:** one logical change per commit. CI must be green before merging.
