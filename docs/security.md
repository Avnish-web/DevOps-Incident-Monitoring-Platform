# Security design and threat model

This document lists what the platform protects, where it can be attacked, and the control that
answers each threat. Every control listed here is implemented and covered by an automated test
or check, named in the last column.

## Assets

| Asset | Why it matters |
|-------|----------------|
| User accounts and sessions | Control over monitors, alert channels and users |
| Alert channel targets | Webhook and Slack URLs embed credentials; e-mail addresses are personal data |
| Internal network reachability | The worker makes HTTP requests on users' behalf (SSRF) |
| Monitoring data | Reveals a customer's infrastructure and its outages |
| Secrets | Database, Redis, encryption key, bootstrap admin password |

## Trust boundaries

```text
Internet ──▶ [WAF + ALB | Nginx] ──▶ API ──▶ PostgreSQL / Redis (internal network only)
                                      ▲
Worker ──▶ Internet (checks, webhooks, SMTP)   Worker ──▶ PostgreSQL
```

Untrusted inputs: every HTTP request, every monitored URL, every response from a monitored
target, every alert channel target, and the DNS answers for all of them.

## Threats and controls (STRIDE)

| # | Threat | Control | Verified by |
|---|--------|---------|-------------|
| **S1** | Credential guessing / stuffing | BCrypt hashes; 5 failures per account and 30 per IP per 15 min in Redis (`429` + `Retry-After`); Nginx 10 login requests/min per IP; WAF rate rule | `AuthApiTests.bruteForceIsLockedOutEvenWithTheRightPassword`, Nginx rate-limit check |
| **S2** | Account enumeration | Identical `401` for unknown user, wrong password and disabled account; DaoAuthenticationProvider equalizes timing | `AuthApiTests.wrongPasswordAndUnknownUserGetTheSameAnswer` |
| **S3** | Session theft via XSS | Session in an HttpOnly, SameSite=Lax (Secure in production) cookie, never readable by JavaScript; strict CSP without inline scripts; React escapes output; lint forbids `innerHTML` | `AuthApiTests.loginSetsHardenedSessionCookieAndReturnsUser`, Nginx header check, dashboard rendering under CSP |
| **S4** | Session fixation | Session id changed at login; CSRF token rotated at login | `AuthController.login` |
| **T1** | Cross-site request forgery | Cookie-to-header CSRF on every state-changing request, including login | `AuthApiTests.stateChangingRequestWithoutCsrfHeaderIsRejected`, `loginWithoutCsrfTokenIsRejected` |
| **T2** | Mass assignment | Unknown JSON fields rejected; read-only fields (`status`, `id`, `ownerId`) not bindable | `MonitorApiTests.createRejectsUnknownAndReadOnlyFields` |
| **T3** | Lost updates | `If-Match`/ETag → `412`; optimistic locking → `409` | `MonitorApiTests.updateWithStaleIfMatchReturns412AndChangesNothing` |
| **T4** | SQL injection | JPA/JDBC parameter binding only; no dynamic SQL from input; sort fields allow-listed | `MonitorApiTests.listRejectsInvalidParameters` |
| **R1** | Unattributable security actions | `AUDIT` logger with structured events (login success/failure/lockout, logout, password change, user created/deleted) with actor, client IP, request id; hashed account ids | `HardeningApiTests.securityEventsAreAuditedWithoutSecrets` |
| **I1** | **SSRF**: user-supplied URLs make the worker reach internal services or cloud metadata (`169.254.169.254`) | Only http(s); no embedded credentials; host names and IP literals checked against loopback/private/link-local/CGNAT/reserved/IPv4-in-IPv6 ranges **when saved**, and again **for every connection** through a guarded DNS resolver (defeats DNS rebinding and redirects to internal hosts); webhooks never follow redirects | `BlockedAddressesTests`, `TargetUrlValidatorTests`, `HttpCheckerTests.redirectToCloudMetadataIsBlocked`, `hostThatNowResolvesToLoopbackIsBlockedBeforeConnecting`, `AlertSenderTests.webhookToInternalAddressIsBlockedAtSendTime`, `AlertingTests.redirectsAreNotFollowed` |
| **I2** | Leaking alert channel credentials | Targets and signing secrets encrypted with AES-256-GCM (`ALERT_ENCRYPTION_KEY`); API returns only masked previews; webhook secret shown once | `SecretCipherTests`, `AlertChannelApiTests.slackChannelTargetIsEncryptedAndMasked` |
| **I3** | Reading other users' data (IDOR) | Every query scoped by owner; other users' resources are `404` (indistinguishable from missing); alerts only go to the owner's channels | `AuthApiTests.usersCannotSeeOrTouchEachOthersMonitors`, `AlertingTests.otherUsersChannelsAreNeverNotified` |
| **I4** | Error messages leaking internals | Generic `500` Problem Details; no stack traces or exception messages; validation messages never echo input | `GlobalExceptionHandlerTests`, `TargetUrlValidatorTests.errorMessageNeverEchoesInput` |
| **I5** | Exposed operational endpoints | Actuator only on internal management ports, never proxied; denied on the public port | `MonitoringApiApplicationTests.actuatorIsNotExposedOnPublicPort`, `ApiMetricsTests.prometheusIsNotOnThePublicPort` |
| **I6** | Secrets in the repository or images | `.env` git-ignored; placeholders only in examples; gitleaks over full history in CI; secrets injected at runtime (Secrets Manager on AWS) | CI `secrets` job |
| **D1** | Worker abused as a traffic generator | Minimum interval 30 s; timeout ≤ 30 s; ≤ 5 redirects; ≤ 1 MB body read; at most 100 monitors and 20 channels per user | `HardeningApiTests.monitorQuotaIsEnforced`, `HttpCheckerTests` |
| **D2** | Request floods / slowloris | WAF rate rule; Nginx per-IP request and connection limits; 1 MB bodies; short header/body timeouts | Nginx checks |
| **D3** | Slow or malicious targets exhausting workers | Hard per-check deadline (connect, headers and slow-drip body); bounded pool with backpressure | `HttpCheckerTests.slowDripBodyIsCutOffAtTheDeadline`, `CheckSchedulerTests.claimsNoMoreThanItCanRun` |
| **D4** | Dependency outage takes the API down | Redis/DB outages return `503` Problem Details and fail readiness, not liveness; the Redis client re-resolves DNS | `SessionStoreUnavailableFilterTests`, container outage tests (phases 3, 5, 14) |
| **E1** | Privilege escalation to admin | `/api/v1/users/**` requires `ROLE_ADMIN`; roles set only by admins; admins cannot delete themselves; deleting a user ends their sessions | `AuthApiTests.onlyAdminsManageUsers`, `deletingAUserEndsTheirSessionsImmediately` |
| **E2** | Container breakout / lateral movement | Non-root users, read-only root filesystems, all capabilities dropped, `no-new-privileges`, resource limits; databases on an internal-only network; tasks without public IPs | Compose and ECS task definitions |
| **E3** | Command execution from user input | No shell execution anywhere; Linux host monitoring is planned as an agent model, not SSH commands | Code review |

## Supply chain

- **Pinned sources:** exact versions for base images and dependencies; GitHub Actions pinned to
  full commit SHAs; Dependabot for updates.
- **CI scanning:** Trivy fails the build on fixable HIGH/CRITICAL vulnerabilities in images, and
  also scans Terraform and Dockerfiles for misconfigurations. CodeQL (`security-extended`) runs for
  Java and TypeScript, dependency review runs on PRs, and `npm audit` covers production dependencies.
- **Signed releases:** images are signed keylessly with cosign and carry SBOM and provenance
  attestations. ECR tags are immutable.
- **Findings fixed during hardening:** Trivy found critical CVEs in the Tomcat version managed by
  Spring Boot 4.1.1 (pinned to 11.0.26 until Boot catches up) and a HIGH in Alpine's `libexpat`
  (images now apply OS security updates at build time).

## Residual risks and future work

- **No MFA or SSO yet.** OIDC login (e.g. an identity provider in front of the dashboard) is the
  next step for multi-tenant use.
- **No password reset flow.** An administrator creates users and can recreate accounts.
- **Quotas are soft.** Two concurrent creates can exceed a quota by one.
- **Channel targets are encrypted with a single key.** Rotating `ALERT_ENCRYPTION_KEY` requires
  re-encrypting stored channels (the versioned `v1:` token format allows adding a key id).
- **One in-flight check after a target change.** A check that was running when a monitor's URL
  changed can count once toward the new state.
