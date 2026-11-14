-- Roles created through the portal (the six built-in roles are not stored here).
CREATE TABLE custom_role (
    name        VARCHAR(30) PRIMARY KEY,
    description VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
