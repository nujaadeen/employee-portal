CREATE TABLE project_repository (
    id             BIGSERIAL PRIMARY KEY,
    project_id     BIGINT       NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    full_name      VARCHAR(200) NOT NULL,
    default_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    html_url       VARCHAR(300),
    CONSTRAINT uq_project_repository UNIQUE (project_id, full_name)
);
CREATE TABLE task_git_link (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT       NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    repo            VARCHAR(200) NOT NULL,
    branch          VARCHAR(200) NOT NULL,
    pr_number       INT,
    pr_title        VARCHAR(300),
    pr_state        VARCHAR(10),
    pr_url          VARCHAR(300),
    checks_status   VARCHAR(10),
    approvals       INT NOT NULL DEFAULT 0,
    last_sha        VARCHAR(64),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_task_git_link UNIQUE (task_id, repo, branch),
    CONSTRAINT ck_pr_state CHECK (pr_state IS NULL OR pr_state IN ('OPEN','MERGED','CLOSED')),
    CONSTRAINT ck_checks CHECK (checks_status IS NULL OR checks_status IN ('PASSING','FAILING','PENDING'))
);
CREATE INDEX idx_task_git_link_sha ON task_git_link (last_sha);
CREATE TABLE task_commit (
    id            BIGSERIAL PRIMARY KEY,
    task_id       BIGINT       NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    repo          VARCHAR(200) NOT NULL,
    sha           VARCHAR(64)  NOT NULL,
    message       TEXT         NOT NULL,
    author_name   VARCHAR(200),
    committed_at  TIMESTAMPTZ  NOT NULL,
    url           VARCHAR(300),
    CONSTRAINT uq_task_commit UNIQUE (task_id, repo, sha)
);
CREATE TABLE pr_review_request (
    id                BIGSERIAL PRIMARY KEY,
    repo              VARCHAR(200) NOT NULL,
    pr_number         INT          NOT NULL,
    pr_title          VARCHAR(300),
    pr_url            VARCHAR(300),
    reviewer_username VARCHAR(100) NOT NULL,
    state             VARCHAR(10)  NOT NULL,   -- PENDING or DONE
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_pr_review UNIQUE (repo, pr_number, reviewer_username)
);
CREATE INDEX idx_pr_review_reviewer ON pr_review_request (reviewer_username, state);
