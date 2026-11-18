# syntax=docker/dockerfile:1.7
# Keycloak with the Baracel realm and login theme baked in (a few hundred KB, so no ConfigMap is needed).
# Build context: the repository root.   docker build -f infra/k8s/images/keycloak/Dockerfile -t baracel/keycloak:local .
#
# The realm file in keycloak/realm/ is written for http://localhost:5173 (Compose). For the local Kubernetes cluster the portal is
# http://portal.baracel.local and Gitea http://git.baracel.local, so the URLs of the clients portal-web and gitea are rewritten HERE, at
# build time, and the source file is left alone. Secrets stay ${PLACEHOLDERS} filled from the container environment.

FROM quay.io/keycloak/keycloak:26.8.2
COPY keycloak/themes /opt/keycloak/themes
COPY keycloak/realm/baracel-realm.json /opt/keycloak/data/import/baracel-realm.json
USER root
RUN sed -i \
      -e 's|http://localhost:5173|http://portal.baracel.local|g' \
      -e 's|http://localhost:3000|http://git.baracel.local|g' \
      /opt/keycloak/data/import/baracel-realm.json \
 && ! grep -q 'localhost' /opt/keycloak/data/import/baracel-realm.json \
 && chown -R 1000:0 /opt/keycloak/data/import /opt/keycloak/themes
USER 1000
