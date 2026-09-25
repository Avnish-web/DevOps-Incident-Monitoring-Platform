# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Report them privately via
GitHub's **Security → Report a vulnerability** (private vulnerability reporting) on this
repository. Include steps to reproduce, the affected version or commit, and the impact you expect.

You will get an acknowledgement within a few days. Fixes are released as a new version, and the
advisory is published once a fix is available.

## Supported versions

Only the latest release receives security fixes.

## Scope

In scope: the API, the worker, the dashboard, the Nginx configuration, the container images, and
the Terraform in `deploy/terraform`. The design and its controls are described in
[docs/security.md](docs/security.md).

Out of scope: findings that require an already-compromised host, database or Redis instance;
denial of service through raw traffic volume (mitigated at the edge by WAF and rate limits); and
self-hosted setups that deliberately set `MONITORING_ALLOW_PRIVATE_TARGETS=true`.
