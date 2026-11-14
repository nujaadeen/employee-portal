# Baracel Employee Portal: rules for the coding agent

## Project
Employee portal for Baracel: Keycloak sign-in, HR (leave and employee records), task tracking linked to a local Gitea, and an AI assistant.
Phase 1 (now): Spring Boot backend, React frontend, Keycloak with a Baracel login theme.
Phase 2 (later, NOT now): a separate FastAPI AI service that Spring calls. In Phase 1 only build the AiAssistantPort and a stub.

## Repository layout
- backend/   Spring Boot service, base package com.baracel.portal
- frontend/  React + TypeScript + Vite app
- keycloak/  realm export (realm/) and login theme (themes/baracel/)
- infra/     docker-compose.yml and env examples
- design/    screens, tokens.css, BRAND.md, logo files (read-only reference, do not edit)
- docs/      openapi.json, decisions.md, progress.md, prompts/

Setup
Use one repository with four folders, so the backend agent and the frontend agent share the same design files and API contract.
baracel-employee-portal/
  CLAUDE.md                 rules for the coding agent (section below)
  design/                   unzip the design kit here
    screens/                9 PNG screenshots of the sample screens
    html/                   the same screens as standalone HTML (read for exact spacing)
    tokens.css              every colour, font, radius, spacing value
    BRAND.md                layout and component rules
    logo/                   put the real Baracel logo files here
  backend/                  Spring Boot service (Prompt 1)
  frontend/                 React app (Prompt 2)
  keycloak/                 realm export (Prompt 1) and login theme (Prompt 2)
  infra/                    docker-compose for Postgres, Keycloak, Gitea, Mailpit
  docs/                     API contract, decisions, run notes

## Backend rules
- Java 21, latest stable Spring Boot, Maven. PostgreSQL with Flyway. spring.jpa.hibernate.ddl-auto=validate.
- One package per feature module: identity, employee, leave, task, gitea, ai, access, common. Inside a module: web (controllers, DTOs), service, domain, repo.
- A module never uses another module's repository. It calls that module's service.
- Controllers return DTO records, never JPA entities. Errors use RFC 7807 ProblemDetail. Timestamps are Instant in UTC, dates are LocalDate.
- Never edit a Flyway migration that already ran. Add a new one.
- Spring is an OAuth2 resource server. Every endpoint needs a valid Keycloak JWT except /actuator/health and the Gitea webhook (which checks an HMAC signature).
- Protect endpoints with @PreAuthorize on permission strings such as leave:approve. Business code never compares role names. Only the access module maps roles to permissions.
- Key every person on the Keycloak user id (the JWT sub claim). Never store passwords. Do not assume users come from Keycloak's own database, because LDAP will be added later.
- Secrets only from environment variables. Commit .env.example, never .env. Do not read or print .env.
- Tests: unit tests for services, and integration tests (MockMvc plus Testcontainers) for every endpoint, including the 403 case for each role that must be blocked. mvn verify must pass.
- Document endpoints with springdoc and export the contract to docs/openapi.json.

## Frontend rules
- React, TypeScript strict mode, Vite, React Router, TanStack Query, oidc-client-ts.
- API types are generated from docs/openapi.json. Do not hand-write API types.
- Colours, fonts, radii and spacing come only from the CSS variables in design/tokens.css. Never hard-code a hex value, font size or radius in a component.
- Follow design/BRAND.md. Before finishing any screen, compare it with the matching PNG in design/screens/ and fix differences.
- Use real buttons, links, labels and inputs. Touch targets are at least 44px. Text contrast is at least 4.5:1.
- Hide UI the user is not allowed to use, based on the permissions from GET /api/me. The backend is still the authority.

## Keycloak rules
- Realm export lives in keycloak/realm/baracel-realm.json. Secrets are placeholders filled from environment variables. Never commit a real secret.
- The login theme lives in keycloak/themes/baracel/login/. Do not edit Keycloak's built-in themes.

## Working rules
- Keep docs/progress.md as a checklist and update it after each step.
- When a requirement is unclear, pick the simplest option that fits the screens, and record the choice in docs/decisions.md. Do not stop to ask unless blocked.
- Do not build Phase 2 (FastAPI) code.
- Make small commits with clear messages. Do not push and do not add a remote.
- Before you say a prompt is done: run the build and the tests, start the stack with docker compose, and report exactly what you ran and anything you could not run or verify.