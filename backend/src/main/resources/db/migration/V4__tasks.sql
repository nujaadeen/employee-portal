CREATE TABLE project (
    id          BIGSERIAL PRIMARY KEY,
    key         VARCHAR(10)  NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    task_seq    INT NOT NULL DEFAULT 0           -- per-project task number sequence
);
CREATE TABLE project_member (
    project_id  BIGINT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    employee_id UUID   NOT NULL REFERENCES employee (id),
    PRIMARY KEY (project_id, employee_id)
);
CREATE TABLE sprint (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT       NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    starts_on  DATE         NOT NULL,
    ends_on    DATE         NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE TABLE task (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT       NOT NULL REFERENCES project (id),
    number      INT          NOT NULL,
    task_key    VARCHAR(20)  NOT NULL UNIQUE,
    title       VARCHAR(300) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL,
    priority    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    assignee_id UUID REFERENCES employee (id),
    reporter_id UUID REFERENCES employee (id),
    due_date    DATE,
    label       VARCHAR(60),
    sprint_id   BIGINT REFERENCES sprint (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_task_number UNIQUE (project_id, number),
    CONSTRAINT ck_task_status CHECK (status IN ('BACKLOG','IN_PROGRESS','IN_REVIEW','DONE'))
);
CREATE INDEX idx_task_assignee ON task (assignee_id, status);
CREATE INDEX idx_task_sprint ON task (sprint_id);
CREATE TABLE subtask (
    id       BIGSERIAL PRIMARY KEY,
    task_id  BIGINT       NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    title    VARCHAR(300) NOT NULL,
    done     BOOLEAN      NOT NULL DEFAULT FALSE,
    position INT          NOT NULL DEFAULT 0
);
CREATE TABLE task_comment (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT      NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    author_id  UUID        NOT NULL REFERENCES employee (id),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE task_activity (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT      NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    actor_id   UUID REFERENCES employee (id),
    actor_name VARCHAR(200) NOT NULL,
    kind       VARCHAR(30)  NOT NULL,
    message    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_activity_task ON task_activity (task_id, created_at);
