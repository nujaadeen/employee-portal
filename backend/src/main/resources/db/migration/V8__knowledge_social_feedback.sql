-- Prompt 06: collection codes match the AI service, knowledge admin, AI feedback, LinkedIn connections, real publishing.

-- 1. The HR collection is called hr-policies in the AI service. Collection access is the permission ai:knowledge:<code>,
--    so "roles with access to a collection" are the roles that hold that permission in the matrix.
UPDATE knowledge_collection SET code = 'hr-policies', name = 'HR policies', permission = 'ai:knowledge:hr-policies' WHERE code = 'hr';
UPDATE permission SET action = 'knowledge:hr-policies', label = 'HR policies knowledge', ui_row = 'HR policies knowledge'
 WHERE module = 'ai' AND action = 'knowledge:hr';
INSERT INTO role_permission (role_name, permission_id)
SELECT 'HR', id FROM permission WHERE module = 'ai' AND action = 'knowledge:hr-policies'
ON CONFLICT DO NOTHING;

-- 2. New permission: manage the knowledge base (ADMIN)
INSERT INTO permission (module, action, label, hint, ui_group, ui_row, ui_column, sort_order)
VALUES ('ai', 'knowledge:manage', 'Manage the knowledge base', 'Collections, sources, who can search what', 'Admin', 'Knowledge base', 'EDIT', 91);
INSERT INTO role_permission (role_name, permission_id)
SELECT 'ADMIN', id FROM permission WHERE module = 'ai' AND action = 'knowledge:manage';

-- 3. Chat: message status, source details, draft platform
ALTER TABLE ai_message ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'COMPLETE';   -- COMPLETE, STOPPED or FAILED
ALTER TABLE ai_message ADD CONSTRAINT ck_ai_message_status CHECK (status IN ('COMPLETE','STOPPED','FAILED'));
ALTER TABLE ai_message_source ADD COLUMN page INT;
ALTER TABLE ai_message_source ADD COLUMN source_ref VARCHAR(1000);
ALTER TABLE ai_draft ADD COLUMN platform VARCHAR(15) NOT NULL DEFAULT 'LINKEDIN';
ALTER TABLE ai_draft ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'en';

CREATE TABLE ai_feedback (
    id         BIGSERIAL PRIMARY KEY,
    message_id UUID         NOT NULL REFERENCES ai_message (id) ON DELETE CASCADE,
    user_id    UUID         NOT NULL REFERENCES employee (id),
    rating     VARCHAR(4)   NOT NULL,
    comment    VARCHAR(1000),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_feedback_rating CHECK (rating IN ('UP','DOWN')),
    CONSTRAINT uq_ai_feedback UNIQUE (message_id, user_id)
);

-- 4. What the admin did in the knowledge base (the AI service has no job list): feeds "Indexing activity"
CREATE TABLE knowledge_activity (
    id         BIGSERIAL PRIMARY KEY,
    job_id     VARCHAR(64)  NOT NULL UNIQUE,
    collection VARCHAR(40)  NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    label      VARCHAR(500) NOT NULL,
    user_id    UUID         NOT NULL REFERENCES employee (id),
    user_name  VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_activity_created ON knowledge_activity (created_at DESC);

-- 5. LinkedIn connections. Tokens are stored encrypted (AES-GCM); see TokenCipher. Never returned by any API.
CREATE TABLE social_connection (
    id                UUID PRIMARY KEY,
    owner_id          UUID REFERENCES employee (id),     -- NULL = the company connection
    provider          VARCHAR(15)  NOT NULL,
    account_urn       VARCHAR(100) NOT NULL,
    display_name      VARCHAR(200) NOT NULL,
    access_token_enc  TEXT         NOT NULL,
    refresh_token_enc TEXT,
    expires_at        TIMESTAMPTZ,
    refresh_expires_at TIMESTAMPTZ,
    scopes            VARCHAR(300) NOT NULL DEFAULT '',
    status            VARCHAR(10)  NOT NULL DEFAULT 'CONNECTED',
    created_by        UUID REFERENCES employee (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_social_conn_provider CHECK (provider IN ('LINKEDIN','INSTAGRAM','FACEBOOK')),
    CONSTRAINT ck_social_conn_status CHECK (status IN ('CONNECTED','EXPIRED','REVOKED'))
);
CREATE UNIQUE INDEX uq_social_conn_personal ON social_connection (owner_id, provider) WHERE owner_id IS NOT NULL AND status <> 'REVOKED';
CREATE UNIQUE INDEX uq_social_conn_company ON social_connection (provider, account_urn) WHERE owner_id IS NULL AND status <> 'REVOKED';

CREATE TABLE social_oauth_state (
    state      VARCHAR(64) PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES employee (id),
    purpose    VARCHAR(30) NOT NULL,                      -- LINKEDIN_PERSONAL or LINKEDIN_COMPANY
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ                                -- single use
);

-- 6. Publishing: one result per (draft, channel). Earlier simulated duplicates are collapsed first.
DELETE FROM publish_log a USING publish_log b WHERE a.draft_id = b.draft_id AND a.channel_code = b.channel_code AND a.id > b.id;
ALTER TABLE publish_log ADD COLUMN reason VARCHAR(20);          -- TOKEN_EXPIRED, RATE_LIMITED, REJECTED, UNKNOWN_ERROR
ALTER TABLE publish_log ADD COLUMN post_id VARCHAR(100);
ALTER TABLE publish_log ADD COLUMN post_url VARCHAR(300);
ALTER TABLE publish_log ADD COLUMN connection_id UUID;
ALTER TABLE publish_log ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE publish_log ALTER COLUMN status TYPE VARCHAR(15);
ALTER TABLE publish_log ADD CONSTRAINT uq_publish_draft_channel UNIQUE (draft_id, channel_code);
