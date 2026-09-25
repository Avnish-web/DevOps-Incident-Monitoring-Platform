# syntax=docker/dockerfile:1.7
# Dashboard: static build served by an unprivileged Nginx that also proxies /api (infra/nginx).
# Build context is the repository root so the Nginx config can be included.

FROM node:26.9.0-alpine3.24 AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.6-alpine3.24
LABEL org.opencontainers.image.title="monitoring-web" \
      org.opencontainers.image.licenses="MIT"
# OS security fixes released after the base image was built (e.g. libexpat).
USER root
RUN apk upgrade --no-cache
COPY infra/nginx/nginx.conf /etc/nginx/nginx.conf
COPY infra/nginx/conf.d/ /etc/nginx/conf.d/
COPY infra/nginx/snippets/ /etc/nginx/snippets/
COPY --from=build /src/dist /usr/share/nginx/html
# The base image runs as uid 101 and listens on 8080; nothing here needs root.
USER 101
EXPOSE 8080 8443
