-- LOCAL / TEST DEMO DATA. Loaded only when the Flyway location classpath:db/seed is on (profile "local" and tests).
-- Employee ids equal the Keycloak user ids in keycloak/realm/baracel-realm.json (b0000000-0000-4000-8000-0000000000NN).

-- ---------- people ----------
INSERT INTO employee (id, keycloak_id, code, first_name, last_name, email, job_title, department, manager_id, status, primary_role, joined_on, location, gitea_username) VALUES
 ('b0000000-0000-4000-8000-000000000003','b0000000-0000-4000-8000-000000000003','EMP-0100','Ruth','Mensah','ruth.mensah@baracel.example','Chief Operating Officer','Management',NULL,'ACTIVE','MANAGER','2022-06-01','Dubai','ruth.mensah'),
 ('b0000000-0000-4000-8000-000000000002','b0000000-0000-4000-8000-000000000002','EMP-0101','Omar','Haddad','omar.haddad@baracel.example','Head of Sales','Sales','b0000000-0000-4000-8000-000000000003','ACTIVE','MANAGER','2023-02-13','Dubai','omar.haddad'),
 ('b0000000-0000-4000-8000-000000000004','b0000000-0000-4000-8000-000000000004','EMP-0110','Lena','Fischer','lena.fischer@baracel.example','HR Manager','People','b0000000-0000-4000-8000-000000000003','ACTIVE','HR','2023-04-03','Dubai','lena.fischer'),
 ('b0000000-0000-4000-8000-000000000008','b0000000-0000-4000-8000-000000000008','EMP-0160','Sara','Qureshi','sara.qureshi@baracel.example','Product Designer','Design','b0000000-0000-4000-8000-000000000003','ONBOARDING','EMPLOYEE','2026-11-02','Dubai','sara.qureshi'),
 ('b0000000-0000-4000-8000-000000000005','b0000000-0000-4000-8000-000000000005','EMP-0120','Daniel','Okoye','daniel.okoye@baracel.example','DevOps Engineer','Engineering','b0000000-0000-4000-8000-000000000003','ACTIVE','ADMIN','2022-09-12','Dubai','daniel.okoye'),
 ('b0000000-0000-4000-8000-000000000009','b0000000-0000-4000-8000-000000000009','EMP-0150','Mei','Tan','mei.tan@baracel.example','Finance Analyst','Finance','b0000000-0000-4000-8000-000000000003','ACTIVE','EMPLOYEE','2024-08-19','Dubai','mei.tan'),
 ('b0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000001','EMP-0142','Aisha','Rahman','aisha.rahman@baracel.example','Sales Executive','Sales','b0000000-0000-4000-8000-000000000002','ACTIVE','SALES','2025-03-03','Dubai','aisha.rahman'),
 ('b0000000-0000-4000-8000-000000000010','b0000000-0000-4000-8000-000000000010','EMP-0157','Karim','Aziz','karim.aziz@baracel.example','Account Manager','Sales','b0000000-0000-4000-8000-000000000002','ACTIVE','SALES','2026-01-12','Dubai','karim.aziz'),
 ('b0000000-0000-4000-8000-000000000011','b0000000-0000-4000-8000-000000000011','EMP-0149','Nadia','Hussein','nadia.hussein@baracel.example','Sales Executive','Sales','b0000000-0000-4000-8000-000000000002','ON_LEAVE','SALES','2025-07-21','Dubai','nadia.hussein'),
 ('b0000000-0000-4000-8000-000000000012','b0000000-0000-4000-8000-000000000012','EMP-0152','Joel','Mathews','joel.mathews@baracel.example','Sales Executive','Sales','b0000000-0000-4000-8000-000000000002','ACTIVE','SALES','2025-10-06','Dubai','joel.mathews'),
 ('b0000000-0000-4000-8000-000000000013','b0000000-0000-4000-8000-000000000013','EMP-0170','Hana','Saleh','hana.saleh@baracel.example','Sales Development Rep','Sales','b0000000-0000-4000-8000-000000000002','ONBOARDING','SALES','2026-11-09','Dubai','hana.saleh'),
 ('b0000000-0000-4000-8000-000000000006','b0000000-0000-4000-8000-000000000006','EMP-0131','Priya','Nair','priya.nair@baracel.example','Backend Engineer','Engineering','b0000000-0000-4000-8000-000000000005','ON_LEAVE','ENGINEER','2024-01-15','Dubai','priya.nair'),
 ('b0000000-0000-4000-8000-000000000007','b0000000-0000-4000-8000-000000000007','EMP-0133','Tom','Becker','tom.becker@baracel.example','QA Engineer','Engineering','b0000000-0000-4000-8000-000000000005','ON_LEAVE','ENGINEER','2024-03-04','Dubai','tom.becker');
ALTER SEQUENCE employee_code_seq RESTART WITH 200;

-- ---------- leave ----------
INSERT INTO public_holiday (holiday_date, name) VALUES ('2026-12-02','National Day'),('2026-12-03','National Day holiday');

INSERT INTO leave_balance (employee_id, leave_type_id, year, entitled, used)
SELECT e.id, t.id, 2026, t.default_days, v.used FROM (VALUES
 ('b0000000-0000-4000-8000-000000000001'::uuid,'ANNUAL',16),('b0000000-0000-4000-8000-000000000001','SICK',2),
 ('b0000000-0000-4000-8000-000000000001','COMP_OFF',1),('b0000000-0000-4000-8000-000000000001','PARENTAL',0),
 ('b0000000-0000-4000-8000-000000000010','ANNUAL',21),('b0000000-0000-4000-8000-000000000011','ANNUAL',9),
 ('b0000000-0000-4000-8000-000000000012','ANNUAL',13),('b0000000-0000-4000-8000-000000000013','ANNUAL',0),
 ('b0000000-0000-4000-8000-000000000002','ANNUAL',12)
) AS v(emp, code, used) JOIN employee e ON e.id = v.emp JOIN leave_type t ON t.code = v.code;

INSERT INTO leave_request (id, employee_id, leave_type_id, from_date, to_date, days, reason, status, decided_by, decided_at, note, created_at)
SELECT v.id, v.emp, t.id, v.f, v.t, v.d, v.reason, v.st, v.by, v.at, NULL, now() - interval '5 days' FROM (VALUES
 ('c0000000-0000-4000-8000-000000000001'::uuid,'b0000000-0000-4000-8000-000000000010'::uuid,'ANNUAL','2026-11-23'::date,'2026-11-27'::date,5,NULL,'PENDING',NULL::uuid,NULL::timestamptz),
 ('c0000000-0000-4000-8000-000000000002','b0000000-0000-4000-8000-000000000012','ANNUAL','2026-11-30','2026-11-30',1,NULL,'PENDING',NULL,NULL),
 ('c0000000-0000-4000-8000-000000000003','b0000000-0000-4000-8000-000000000011','ANNUAL','2026-11-13','2026-11-16',2,'Family trip','APPROVED','b0000000-0000-4000-8000-000000000002',now() - interval '4 days'),
 ('c0000000-0000-4000-8000-000000000004','b0000000-0000-4000-8000-000000000007','SICK','2026-11-13','2026-11-16',2,NULL,'APPROVED','b0000000-0000-4000-8000-000000000005',now() - interval '1 day'),
 ('c0000000-0000-4000-8000-000000000005','b0000000-0000-4000-8000-000000000006','ANNUAL','2026-11-09','2026-11-18',8,'Home visit','APPROVED','b0000000-0000-4000-8000-000000000005',now() - interval '20 days'),
 ('c0000000-0000-4000-8000-000000000006','b0000000-0000-4000-8000-000000000013','ANNUAL','2026-11-20','2026-11-20',1,NULL,'APPROVED','b0000000-0000-4000-8000-000000000002',now() - interval '2 days'),
 ('c0000000-0000-4000-8000-000000000007','b0000000-0000-4000-8000-000000000010','SICK','2026-10-02','2026-10-02',1,NULL,'APPROVED','b0000000-0000-4000-8000-000000000002','2026-10-02 08:00+00')
) AS v(id, emp, code, f, t, d, reason, st, by, at) JOIN leave_type t ON t.code = v.code;

INSERT INTO notification (employee_id, type, title, body, link, created_at) VALUES
 ('b0000000-0000-4000-8000-000000000002','LEAVE_SUBMITTED','Leave request from Karim Aziz','Karim Aziz requested 5 day(s) of annual leave, 2026-11-23 to 2026-11-27.','/leave', now() - interval '5 days'),
 ('b0000000-0000-4000-8000-000000000002','LEAVE_SUBMITTED','Leave request from Joel Mathews','Joel Mathews requested 1 day(s) of annual leave, 2026-11-30 to 2026-11-30.','/leave', now() - interval '3 days');

INSERT INTO announcement (title, body, posted_by, author_id, created_at) VALUES
 ('Year-end leave carry-over opens on 1 December','Unused annual leave can be carried over from 1 December. Ask HR if you are unsure.','HR','b0000000-0000-4000-8000-000000000004', now() - interval '2 days'),
 ('Gitea maintenance window, Sunday 22:00 to 23:00','Gitea will be read-only for one hour.','Engineering','b0000000-0000-4000-8000-000000000005', now() - interval '4 days');

-- ---------- tasks ----------
INSERT INTO project (id, key, name, description, task_seq) VALUES (1, 'BAR', 'Platform', 'Baracel platform: OTP, QR, onboarding and HR tooling.', 157);
INSERT INTO project_member (project_id, employee_id)
SELECT 1, id FROM employee WHERE id NOT IN (
 'b0000000-0000-4000-8000-000000000010','b0000000-0000-4000-8000-000000000011','b0000000-0000-4000-8000-000000000012','b0000000-0000-4000-8000-000000000013');
INSERT INTO sprint (id, project_id, name, starts_on, ends_on, active) VALUES (1, 1, 'Sprint 23', '2026-11-09', '2026-11-20', TRUE);

INSERT INTO task (id, project_id, number, task_key, title, description, status, priority, assignee_id, reporter_id, due_date, label, sprint_id, created_at) VALUES
 (1, 1,151,'BAR-151','Add Telegram fallback to OTP',NULL,'BACKLOG','MEDIUM','b0000000-0000-4000-8000-000000000006','b0000000-0000-4000-8000-000000000002','2026-11-27','Backend',1,'2026-11-05 09:00+00'),
 (2, 1,153,'BAR-153','Merchant onboarding checklist UI',NULL,'BACKLOG','MEDIUM','b0000000-0000-4000-8000-000000000008','b0000000-0000-4000-8000-000000000002','2026-11-20','Frontend',1,'2026-11-05 09:10+00'),
 (3, 1,157,'BAR-157','Rate limit OTP resend',NULL,'BACKLOG','HIGH','b0000000-0000-4000-8000-000000000005','b0000000-0000-4000-8000-000000000002','2026-11-30','Security',1,'2026-11-06 09:00+00'),
 (4, 1,142,'BAR-142','WhatsApp OTP delivery','Deliver one-time codes through the WhatsApp Business API, with Telegram and email as fallbacks. Codes expire after five minutes and each phone number is limited to three requests per hour.','IN_PROGRESS','HIGH','b0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000002','2026-11-13','Backend',1,'2026-11-03 09:00+00'),
 (5, 1,146,'BAR-146','Partner webhook retries','Retry failed partner webhooks with exponential backoff.','IN_PROGRESS','MEDIUM','b0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000002','2026-11-17','Backend',1,'2026-11-04 09:00+00'),
 (6, 1,149,'BAR-149','Leave calendar export',NULL,'IN_PROGRESS','MEDIUM','b0000000-0000-4000-8000-000000000009','b0000000-0000-4000-8000-000000000004','2026-11-19','HR module',1,'2026-11-04 10:00+00'),
 (7, 1,138,'BAR-138','Static QR code generation',NULL,'IN_REVIEW','HIGH','b0000000-0000-4000-8000-000000000007','b0000000-0000-4000-8000-000000000002','2026-11-18','Backend',1,'2026-10-28 09:00+00'),
 (8, 1,140,'BAR-140','Fix timezone in payroll report',NULL,'IN_REVIEW','MEDIUM','b0000000-0000-4000-8000-000000000009','b0000000-0000-4000-8000-000000000004','2026-11-16','HR module',1,'2026-10-29 09:00+00'),
 (9, 1,131,'BAR-131','Keycloak realm export',NULL,'DONE','MEDIUM','b0000000-0000-4000-8000-000000000005','b0000000-0000-4000-8000-000000000005','2026-11-06','Platform',1,'2026-10-20 09:00+00'),
 (10,1,134,'BAR-134','Gitea webhook receiver',NULL,'DONE','MEDIUM','b0000000-0000-4000-8000-000000000005','b0000000-0000-4000-8000-000000000005','2026-11-09','Platform',1,'2026-10-21 09:00+00'),
 (11,1,129,'BAR-129','SSO login screen',NULL,'DONE','MEDIUM','b0000000-0000-4000-8000-000000000008','b0000000-0000-4000-8000-000000000005','2026-11-10','Frontend',1,'2026-10-19 09:00+00'),
 (12,1,144,'BAR-144','Merchant FAQ refresh',NULL,'BACKLOG','LOW','b0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000002','2026-11-19','Sales',NULL,'2026-11-02 09:00+00'),
 (13,1,145,'BAR-145','Q4 pipeline review deck',NULL,'IN_PROGRESS','MEDIUM','b0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000002','2026-11-20','Sales',NULL,'2026-11-02 09:30+00'),
 (14,1,147,'BAR-147','Update partner price sheet',NULL,'BACKLOG','LOW','b0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000002','2026-11-25','Sales',NULL,'2026-11-02 10:00+00'),
 (15,1,148,'BAR-148','Prepare QR launch brief',NULL,'BACKLOG','MEDIUM','b0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000002','2026-11-26','Sales',NULL,'2026-11-03 09:00+00'),
 (16,1,150,'BAR-150','Customer reference list',NULL,'BACKLOG','LOW','b0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000002','2026-12-01','Sales',NULL,'2026-11-03 09:30+00');
SELECT setval(pg_get_serial_sequence('project','id'), 1);
SELECT setval(pg_get_serial_sequence('sprint','id'), 1);
SELECT setval(pg_get_serial_sequence('task','id'), 16);

INSERT INTO subtask (task_id, title, done, position) VALUES
 (4,'Provider client and template registration',TRUE,0),(4,'Send endpoint with retries',TRUE,1),
 (4,'Rate limiting per phone number',FALSE,2),(4,'Integration tests against sandbox',FALSE,3),
 (5,'Backoff policy',TRUE,0),(5,'Dead-letter queue',FALSE,1);

INSERT INTO task_comment (task_id, author_id, body, created_at) VALUES
 (4,'b0000000-0000-4000-8000-000000000005','please add the template id to the config','2026-11-13 11:02+00');

INSERT INTO task_activity (task_id, actor_id, actor_name, kind, message, created_at)
SELECT t.id, t.reporter_id, e.first_name || ' ' || e.last_name, 'CREATED', 'created this task', t.created_at
FROM task t JOIN employee e ON e.id = t.reporter_id;
INSERT INTO task_activity (task_id, actor_id, actor_name, kind, message, created_at) VALUES
 (4,'b0000000-0000-4000-8000-000000000001','Aisha Rahman','STATUS','moved this from Backlog to In progress','2026-11-10 05:30+00'),
 (4,'b0000000-0000-4000-8000-000000000001','Aisha Rahman','PR','opened pull request #57 in baracel/otp-service','2026-11-13 10:20+00'),
 (4,'b0000000-0000-4000-8000-000000000005','Daniel Okoye','COMMENT','commented: please add the template id to the config','2026-11-13 11:02+00');

-- ---------- gitea ----------
INSERT INTO project_repository (project_id, full_name, default_branch, html_url) VALUES
 (1,'baracel/otp-service','main','http://localhost:3000/baracel/otp-service'),
 (1,'baracel/portal','main','http://localhost:3000/baracel/portal');
INSERT INTO task_git_link (task_id, repo, branch, pr_number, pr_title, pr_state, pr_url, checks_status, approvals, last_sha, updated_at) VALUES
 (4,'baracel/otp-service','feature/bar-142-whatsapp-otp',57,'Add WhatsApp OTP delivery','OPEN','http://localhost:3000/baracel/otp-service/pulls/57','PASSING',0,'a41c9e2f0b7d4c1e8a93d5b6f7c20e1d4a8b9c03', now() - interval '2 minutes'),
 (5,'baracel/otp-service','feature/bar-146-webhook-retries',NULL,NULL,NULL,NULL,NULL,0,'3c5d7e9f1a2b4c6d8e0f1a3b5c7d9e1f2a4b6c8d', now() - interval '1 day'),
 (7,'baracel/otp-service','feature/bar-138-static-qr',54,'Static QR code generation','OPEN','http://localhost:3000/baracel/otp-service/pulls/54',NULL,0,'5e7f9a1b3c4d6e8f0a2b4c6d8e0f1a3b5c7d9e1f', now() - interval '1 day'),
 (8,'baracel/portal','feature/bar-140-payroll-timezone',55,'Fix timezone in payroll report','OPEN','http://localhost:3000/baracel/portal/pulls/55','FAILING',0,'6f8a0b2c4d5e7f9a1b3c5d7e9f0a2b4c6d8e0f1a', now() - interval '1 day'),
 (9,'baracel/portal','feature/bar-131-realm-export',51,'Keycloak realm export','MERGED','http://localhost:3000/baracel/portal/pulls/51','PASSING',1,'7a9b1c3d5e6f8a0b2c4d6e8f0a1b3c5d7e9f1a2b', now() - interval '6 days'),
 (10,'baracel/portal','feature/bar-134-webhook-receiver',52,'Gitea webhook receiver','MERGED','http://localhost:3000/baracel/portal/pulls/52','PASSING',1,'8b0c2d4e6f7a9b1c3d5e7f9a0b2c4d6e8f0a1b3c', now() - interval '5 days'),
 (11,'baracel/portal','feature/bar-129-sso-login',49,'SSO login screen','MERGED','http://localhost:3000/baracel/portal/pulls/49','PASSING',1,'9c1d3e5f7a8b0c2d4e6f8a0b1c3d5e7f9a1b2c4d', now() - interval '4 days');
INSERT INTO task_commit (task_id, repo, sha, message, author_name, committed_at, url) VALUES
 (4,'baracel/otp-service','a41c9e2f0b7d4c1e8a93d5b6f7c20e1d4a8b9c03','Add WhatsApp client and retry policy','Aisha Rahman', now() - interval '2 hours','http://localhost:3000/baracel/otp-service/commit/a41c9e2'),
 (4,'baracel/otp-service','7be03d1c5a2f4e8b9d6a1c3e7f0b2d4a6c8e1f35','Register message templates','Aisha Rahman', now() - interval '1 day','http://localhost:3000/baracel/otp-service/commit/7be03d1');
INSERT INTO pr_review_request (repo, pr_number, pr_title, pr_url, reviewer_username, state) VALUES
 ('baracel/portal',61,'Add leave export endpoint','http://localhost:3000/baracel/portal/pulls/61','aisha.rahman','PENDING'),
 ('baracel/otp-service',58,'Template id in config','http://localhost:3000/baracel/otp-service/pulls/58','aisha.rahman','PENDING'),
 ('baracel/portal',62,'Directory search by team','http://localhost:3000/baracel/portal/pulls/62','aisha.rahman','PENDING');

-- ---------- AI ----------
INSERT INTO social_channel (code, name, type, provider, connected, owner_id) VALUES
 ('personal-linkedin-aisha','My LinkedIn (personal)','PERSONAL','LINKEDIN',TRUE,'b0000000-0000-4000-8000-000000000001');
INSERT INTO ai_conversation (id, owner_id, title, created_at, updated_at) VALUES
 ('d0000000-0000-4000-8000-000000000001','b0000000-0000-4000-8000-000000000001','QR launch ad copy', now(), now()),
 ('d0000000-0000-4000-8000-000000000002','b0000000-0000-4000-8000-000000000001','Ramadan offer ideas', now() - interval '1 hour', now() - interval '1 hour'),
 ('d0000000-0000-4000-8000-000000000003','b0000000-0000-4000-8000-000000000001','Merchant email sequence', now() - interval '8 days', now() - interval '8 days'),
 ('d0000000-0000-4000-8000-000000000004','b0000000-0000-4000-8000-000000000001','LinkedIn post: partner news', now() - interval '9 days', now() - interval '9 days');
INSERT INTO ai_message (id, conversation_id, role, content, created_at) VALUES
 ('e0000000-0000-4000-8000-000000000001','d0000000-0000-4000-8000-000000000001','USER','Write an Instagram and LinkedIn ad for our new merchant QR payments product. Friendly, short, and use our brand voice.', now() - interval '1 minute'),
 ('e0000000-0000-4000-8000-000000000002','d0000000-0000-4000-8000-000000000001','ASSISTANT','Here is a draft that follows the brand voice guide (plain, warm, no jargon). I used the product brief for the claims. Pick where to publish, or ask me to change the tone.', now());
INSERT INTO ai_message_source (message_id, title, collection) VALUES
 ('e0000000-0000-4000-8000-000000000002','Product brief v3.pdf','product'),('e0000000-0000-4000-8000-000000000002','Brand voice guide','brand');
INSERT INTO ai_draft (id, message_id, kind, headline, caption, hashtags, suggested_channels) VALUES
 ('f0000000-0000-4000-8000-000000000001','e0000000-0000-4000-8000-000000000002','SOCIAL_POST','Scan. Pay. Done.',
  'Your customers already scan to pay. Now your counter can too. Set up Baracel QR in minutes and see every payment in one place.',
  '#Baracel #QRPayments #SmallBusiness','company-instagram,company-linkedin');
