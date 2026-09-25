#!/usr/bin/env sh
# Creates a self-signed certificate for https://localhost (local TLS testing only).
# Browsers will warn about it; never use it for a real deployment.
set -eu
# Git Bash on Windows would rewrite "/CN=..." into a Windows path; no effect elsewhere.
export MSYS_NO_PATHCONV=1

dir="$(cd "$(dirname "$0")/.." && pwd)/infra/nginx/certs"
mkdir -p "$dir"
cd "$dir"   # relative file names work with every openssl build, including Windows

openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 30 \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
  -keyout privkey.pem -out fullchain.pem

# The unprivileged Nginx (uid 101) must be able to read the key inside the container.
chmod 644 fullchain.pem privkey.pem
echo "Wrote $dir/fullchain.pem and $dir/privkey.pem (valid 30 days)"
