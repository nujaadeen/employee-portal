CREATE TABLE knowledge_collection (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(40)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    doc_count  INT          NOT NULL DEFAULT 0,
    permission VARCHAR(80)  NOT NULL,   -- permission string that grants access, e.g. ai:knowledge:product
    sort_order INT          NOT NULL DEFAULT 0
);
INSERT INTO knowledge_collection (code, name, doc_count, permission, sort_order) VALUES
 ('product','Product catalogue',214,'ai:knowledge:product',1),
 ('brand','Brand guidelines',18,'ai:knowledge:brand',2),
 ('campaign','Campaign archive',96,'ai:knowledge:campaign',3),
 ('hr','HR policies',42,'ai:knowledge:hr',4),
 ('engineering','Engineering docs',130,'ai:knowledge:engineering',5);

CREATE TABLE social_channel (
    id        BIGSERIAL PRIMARY KEY,
    code      VARCHAR(60)  NOT NULL UNIQUE,
    name      VARCHAR(100) NOT NULL,
    type      VARCHAR(10)  NOT NULL,
    provider  VARCHAR(15)  NOT NULL,
    connected BOOLEAN      NOT NULL DEFAULT TRUE,
    owner_id  UUID REFERENCES employee (id),
    CONSTRAINT ck_channel_type CHECK (type IN ('COMPANY','PERSONAL')),
    CONSTRAINT ck_channel_provider CHECK (provider IN ('INSTAGRAM','LINKEDIN','FACEBOOK'))
);
INSERT INTO social_channel (code, name, type, provider, connected) VALUES
 ('company-instagram','Company Instagram','COMPANY','INSTAGRAM',TRUE),
 ('company-linkedin','Company LinkedIn','COMPANY','LINKEDIN',TRUE),
 ('company-facebook','Company Facebook','COMPANY','FACEBOOK',TRUE);

CREATE TABLE ai_conversation (
    id         UUID PRIMARY KEY,
    owner_id   UUID         NOT NULL REFERENCES employee (id),
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE TABLE ai_message (
    id              UUID PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES ai_conversation (id) ON DELETE CASCADE,
    role            VARCHAR(10) NOT NULL,
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_role CHECK (role IN ('USER','ASSISTANT'))
);
CREATE INDEX idx_ai_message_conv ON ai_message (conversation_id, created_at);
CREATE TABLE ai_message_source (
    id         BIGSERIAL PRIMARY KEY,
    message_id UUID         NOT NULL REFERENCES ai_message (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    collection VARCHAR(40)  NOT NULL
);
CREATE TABLE ai_draft (
    id                 UUID PRIMARY KEY,
    message_id         UUID         NOT NULL REFERENCES ai_message (id) ON DELETE CASCADE,
    kind               VARCHAR(20)  NOT NULL,
    headline           VARCHAR(200) NOT NULL,
    caption            TEXT         NOT NULL,
    hashtags           VARCHAR(500) NOT NULL DEFAULT '',
    suggested_channels VARCHAR(500) NOT NULL DEFAULT ''
);
CREATE TABLE publish_log (
    id           BIGSERIAL PRIMARY KEY,
    draft_id     UUID         NOT NULL REFERENCES ai_draft (id),
    channel_code VARCHAR(60)  NOT NULL,
    user_id      UUID         NOT NULL REFERENCES employee (id),
    user_name    VARCHAR(200) NOT NULL,
    status       VARCHAR(15)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
