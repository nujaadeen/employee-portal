# syntax=docker/dockerfile:1.7
# Portal frontend: Vite build served by nginx. Build context: the repository root.
#   docker build -f frontend/Dockerfile -t baracel/portal-frontend:local .
#
# One image for every environment: the bundle is built with placeholders (__VITE_API_URL__ ...) and the container start script
# puts the real values from its environment into the bundle and into /config.json (see infra/k8s/images/frontend/).

FROM node:22.12-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
# design/tokens.css is imported through the @design alias, so it must be in the context too
COPY design/ /design/
ENV VITE_API_URL=__VITE_API_URL__ \
    VITE_OIDC_AUTHORITY=__VITE_OIDC_AUTHORITY__ \
    VITE_OIDC_CLIENT_ID=__VITE_OIDC_CLIENT_ID__
RUN npx vite build

FROM nginxinc/nginx-unprivileged:1.27-alpine
USER root
COPY --from=build /build/dist /usr/share/nginx/html
COPY infra/k8s/images/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY infra/k8s/images/frontend/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/40-runtime-config.sh && chown -R 101:101 /usr/share/nginx/html
USER 101
ENV VITE_API_URL=http://localhost:8080 \
    VITE_OIDC_AUTHORITY=http://localhost:8180/realms/baracel \
    VITE_OIDC_CLIENT_ID=portal-web
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
