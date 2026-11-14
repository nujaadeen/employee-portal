-- Access module: permission catalogue and role matrix. Roles live in Keycloak; only their names are stored here.
CREATE TABLE permission (
    id          BIGSERIAL PRIMARY KEY,
    module      VARCHAR(40)  NOT NULL,
    action      VARCHAR(60)  NOT NULL,
    label       VARCHAR(120) NOT NULL,
    hint        VARCHAR(160),
    ui_group    VARCHAR(60)  NOT NULL,
    ui_row      VARCHAR(80)  NOT NULL,
    ui_column   VARCHAR(10)  NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_permission UNIQUE (module, action)
);

CREATE TABLE role_permission (
    id            BIGSERIAL PRIMARY KEY,
    role_name     VARCHAR(40) NOT NULL,
    permission_id BIGINT      NOT NULL REFERENCES permission (id) ON DELETE CASCADE,
    CONSTRAINT uq_role_permission UNIQUE (role_name, permission_id)
);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    UUID,
    actor_name  VARCHAR(200),
    action      VARCHAR(80)  NOT NULL,
    target_type VARCHAR(60),
    target_id   VARCHAR(100),
    detail      TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC);

INSERT INTO permission (module, action, label, hint, ui_group, ui_row, ui_column, sort_order) VALUES
 ('profile','read-own',  'View own profile',      'Own record only',      'People and HR','My profile and leave','VIEW',   10),
 ('leave','request',     'Request leave',         'Own record only',      'People and HR','My profile and leave','CREATE', 11),
 ('profile','edit-own',  'Edit own profile',      'Own record only',      'People and HR','My profile and leave','EDIT',   12),
 ('leave','approve',     'Approve leave',         'Direct reports (HR: everyone)','People and HR','Team leave approvals','APPROVE',20),
 ('employee','team',     'See my team',           'Direct reports',       'People and HR','Team leave approvals','VIEW',   21),
 ('employee','directory','Employee directory',    'Name, team, contact',  'People and HR','Employee directory','VIEW',    30),
 ('employee','records',  'Read all HR records',   'Full employee records','People and HR','HR records','VIEW',               40),
 ('employee','manage',   'Manage HR records',     'Edit, invite, approve','People and HR','HR records','EDIT',               41),
 ('task','view',         'View tasks',            'Projects they belong to','Work','Tasks','VIEW',                          50),
 ('task','create',       'Create tasks',          'Projects they belong to','Work','Tasks','CREATE',                        51),
 ('task','edit',         'Edit tasks',            'Projects they belong to','Work','Tasks','EDIT',                          52),
 ('repo','view',         'View Gitea repositories','Linked through tasks','Work','Gitea repositories','VIEW',                 60),
 ('ai','chat',           'Chat with the assistant','Uses only allowed knowledge','AI assistant','Chat with the assistant','VIEW',70),
 ('ai','knowledge:product',    'Product knowledge',    'Product catalogue','AI assistant','Product knowledge','VIEW',          71),
 ('ai','knowledge:brand',      'Brand knowledge',      'Brand guidelines', 'AI assistant','Brand knowledge','VIEW',            72),
 ('ai','knowledge:campaign',   'Campaign knowledge',   'Campaign archive', 'AI assistant','Campaign knowledge','VIEW',         73),
 ('ai','knowledge:engineering','Engineering knowledge','Engineering docs', 'AI assistant','Engineering knowledge','VIEW',      74),
 ('ai','knowledge:hr',         'HR knowledge',         'HR policies',      'AI assistant','HR knowledge','VIEW',               75),
 ('social','publish:company', 'Publish to company accounts','Instagram, LinkedIn, Facebook','Social publishing','Company accounts','PUBLISH',80),
 ('social','publish:personal','Publish to personal accounts','Own linked accounts','Social publishing','Personal accounts','PUBLISH',81),
 ('access','manage',     'Manage roles and access','Roles, matrix, sign-in methods','Admin','Roles and access','EDIT',        90);

-- Seed matrix (permission = module:action). EMPLOYEE is the base; other roles add to it.
INSERT INTO role_permission (role_name, permission_id)
SELECT r.role_name, p.id FROM (VALUES
 ('EMPLOYEE','profile:read-own'),('EMPLOYEE','profile:edit-own'),('EMPLOYEE','leave:request'),
 ('EMPLOYEE','employee:directory'),('EMPLOYEE','task:view'),('EMPLOYEE','task:create'),('EMPLOYEE','task:edit'),('EMPLOYEE','ai:chat'),
 ('MANAGER','leave:approve'),('MANAGER','employee:team'),
 ('HR','leave:approve'),('HR','employee:team'),('HR','employee:records'),('HR','employee:manage'),
 ('SALES','ai:knowledge:product'),('SALES','ai:knowledge:brand'),('SALES','ai:knowledge:campaign'),
 ('SALES','social:publish:company'),('SALES','social:publish:personal'),
 ('ENGINEER','repo:view'),('ENGINEER','ai:knowledge:engineering')
) AS r(role_name, perm) JOIN permission p ON p.module || ':' || p.action = r.perm;

-- ADMIN: everything
INSERT INTO role_permission (role_name, permission_id) SELECT 'ADMIN', id FROM permission;
