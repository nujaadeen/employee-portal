CREATE TABLE leave_type (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,
    name         VARCHAR(60)  NOT NULL,
    default_days INT,                      -- NULL = no allowance limit (Unpaid)
    sort_order   INT NOT NULL DEFAULT 0
);
INSERT INTO leave_type (code, name, default_days, sort_order) VALUES
 ('ANNUAL','Annual',30,1),('SICK','Sick',10,2),('COMP_OFF','Comp-off',3,3),('PARENTAL','Parental',5,4),('UNPAID','Unpaid',NULL,5);

CREATE TABLE leave_balance (
    id            BIGSERIAL PRIMARY KEY,
    employee_id   UUID   NOT NULL REFERENCES employee (id),
    leave_type_id BIGINT NOT NULL REFERENCES leave_type (id),
    year          INT    NOT NULL,
    entitled      INT    NOT NULL,
    used          INT    NOT NULL DEFAULT 0,
    CONSTRAINT uq_leave_balance UNIQUE (employee_id, leave_type_id, year)
);

CREATE TABLE leave_request (
    id            UUID PRIMARY KEY,
    employee_id   UUID        NOT NULL REFERENCES employee (id),
    leave_type_id BIGINT      NOT NULL REFERENCES leave_type (id),
    from_date     DATE        NOT NULL,
    to_date       DATE        NOT NULL,
    days          INT         NOT NULL,
    reason        VARCHAR(500),
    status        VARCHAR(20) NOT NULL,
    decided_by    UUID REFERENCES employee (id),
    decided_at    TIMESTAMPTZ,
    note          VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_leave_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_leave_dates CHECK (from_date <= to_date)
);
CREATE INDEX idx_leave_request_employee ON leave_request (employee_id, from_date);
CREATE INDEX idx_leave_request_dates ON leave_request (from_date, to_date);

CREATE TABLE public_holiday (
    id           BIGSERIAL PRIMARY KEY,
    holiday_date DATE         NOT NULL UNIQUE,
    name         VARCHAR(120) NOT NULL
);
