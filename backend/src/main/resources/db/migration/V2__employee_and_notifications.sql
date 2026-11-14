CREATE SEQUENCE employee_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE employee (
    id              UUID PRIMARY KEY,
    keycloak_id     VARCHAR(64)  NOT NULL UNIQUE,   -- JWT sub
    code            VARCHAR(20)  NOT NULL UNIQUE,   -- EMP-0001
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL DEFAULT '',
    email           VARCHAR(255) NOT NULL,
    job_title       VARCHAR(150),
    department      VARCHAR(100),
    manager_id      UUID REFERENCES employee (id),
    status          VARCHAR(20)  NOT NULL,
    primary_role    VARCHAR(40),
    joined_on       DATE,
    phone           VARCHAR(50),
    location        VARCHAR(100),
    gitea_username  VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_employee_status CHECK (status IN ('ACTIVE','ON_LEAVE','ONBOARDING','PENDING_APPROVAL','DEACTIVATED'))
);
CREATE INDEX idx_employee_manager ON employee (manager_id);
CREATE INDEX idx_employee_department ON employee (department);
CREATE INDEX idx_employee_gitea ON employee (gitea_username);

CREATE TABLE notification (
    id          BIGSERIAL PRIMARY KEY,
    employee_id UUID         NOT NULL REFERENCES employee (id),
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    link        VARCHAR(200),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_employee ON notification (employee_id, created_at DESC);

CREATE TABLE announcement (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    posted_by  VARCHAR(100) NOT NULL,
    author_id  UUID REFERENCES employee (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
