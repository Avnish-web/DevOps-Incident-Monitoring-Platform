# Production-readiness review

A final review of the platform against common production-readiness criteria. ✅ = in place and
verified; ⚠️ = acceptable with a known limitation; ⏭️ = deliberately left for later.

## Reliability

| Item | Status | Evidence |
|------|--------|----------|
| No single point of failure in the design | ✅ | ≥ 2 tasks per service across 2 AZs, Multi-AZ RDS, Redis failover replica (Terraform) |
| Stateless services | ✅ | Sessions in Redis; schedule and outbox in PostgreSQL |
| Horizontal scaling of checks without duplicates | ✅ | `SKIP LOCKED` claiming; 8-thread concurrency test; live run with 2 workers |
| Backpressure | ✅ | Workers claim only what they can run (`CheckSchedulerTests`) |
| Timeouts on every outbound call | ✅ | Hard per-check deadline; 10 s alert HTTP; DB connection/statement timeouts; Redis 2 s |
| Graceful shutdown | ✅ | SIGTERM verified in containers (scheduler drains, then pools close) |
| Dependency outages | ✅ | DB/Redis down → readiness 503, liveness stays up, automatic recovery (verified live); Redis outage → JSON 503 |
| No lost alerts | ✅ | Transactional outbox, retries with backoff, idempotency index |
| Safe schema changes | ✅ | Flyway, API-only migrations, Hibernate `validate`; worker rolls out after the API (Terraform `wait_for_steady_state`) |
| Rollback | ✅ | Immutable image tags; ECS circuit breaker with automatic rollback |
| Data retention | ✅ | Batched retention job with an advisory lock |
| Backups | ✅ | RDS automated backups (7 days), final snapshot; `pg_dump` procedure for Compose |
| Load tested at scale | ⏭️ | Functional concurrency tests only. A load test (e.g. 10k monitors) would size `WORKER_CONCURRENCY` and the DB instance. |

## Security

See [security.md](security.md) for the threat model.

| Item | Status | Evidence |
|------|--------|----------|
| Authentication and authorization | ✅ | Session auth, CSRF, lockout, per-user isolation, admin role |
| Input validation | ✅ | Bean Validation, unknown-field rejection, allow-listed sorting, SSRF validation |
| Secrets management | ✅ | Env vars / Secrets Manager; no defaults; gitleaks clean history |
| Encryption in transit | ✅ | ALB TLS 1.2/1.3, RDS `force_ssl`, ElastiCache TLS |
| Encryption at rest | ✅ | RDS, ElastiCache, S3 and ECR encryption; alert targets AES-GCM in the database |
| Vulnerability management | ✅ | Trivy (0 fixable HIGH/CRITICAL after patching Tomcat and libexpat), CodeQL, Dependabot, npm audit |
| Least privilege | ✅ / ⚠️ | Non-root read-only containers; task roles with no AWS permissions; the deploy role is broad (PowerUser + prefixed IAM), which is typical for Terraform deployers |
| Audit trail | ✅ | `AUDIT` logger for security events |
| MFA / SSO | ⏭️ | Planned (OIDC) |

## Observability

| Item | Status | Evidence |
|------|--------|----------|
| Structured logs with correlation | ✅ | ECS JSON, `requestId` and `monitorId` in MDC, proxy request id propagated |
| Metrics | ✅ | Micrometer/Prometheus with bounded cardinality |
| Dashboards | ✅ | Provisioned Grafana dashboard; every query verified against live data |
| Alerting on the platform itself | ✅ | Prometheus rules with unit tests; CloudWatch alarms incl. "no worker running" |
| Distributed tracing | ⏭️ | Request ids correlate logs; OpenTelemetry tracing could be added via Micrometer Tracing |

## Delivery

| Item | Status | Evidence |
|------|--------|----------|
| CI on every change | ✅ | Build, tests, lint, types, image scans, IaC checks, secret scan, CodeQL |
| Reproducible, signed artifacts | ✅ | Multi-arch images with SBOM, provenance and cosign signature |
| Infrastructure as code | ✅ | Terraform validated in CI; state in S3 with native locking |
| Gated deployment | ✅ | Manual workflow, `production` environment approval, OIDC (no stored keys) |
| Workflows run on GitHub | ⚠️ | Linted (actionlint) and every step exercised locally; first run happens after pushing to GitHub |
| AWS deployment executed | ⚠️ | Terraform validated against the AWS provider; not applied (requires the owner's account and costs money) |

## Known limitations

- **Quotas are soft** under concurrent creation, by at most one.
- **One stale result after a target change:** a check that was in flight when a monitor's target
  changed can count once toward the new state.
- **HTTP checks only:** no keyword/body assertion yet.
- **Single encryption key version** for alert targets. Rotation needs a re-encryption job; the
  token format is versioned for it.
