-- Pracownicy placówki, ich grafiki tygodniowe i wyjątki (urlopy, dodatkowe zmiany).
CREATE TABLE employees (
    id             uuid        PRIMARY KEY,
    tenant_id      uuid        NOT NULL,
    user_id        uuid        NOT NULL UNIQUE,
    type           text        NOT NULL CHECK (type IN ('DOCTOR', 'MEDICAL_STAFF', 'RECEPTIONIST')),
    first_name     text        NOT NULL CHECK (length(btrim(first_name)) > 0),
    last_name      text        NOT NULL CHECK (length(btrim(last_name)) > 0),
    specialization text,
    active         boolean     NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    version        bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_employees_type ON employees (tenant_id, type, active);

-- Grafik tygodniowy w czasie lokalnym placówki (ADR-B04). Ograniczenie wykluczające odrzuca nakładające się dyżury
-- tego samego pracownika w ten sam dzień tygodnia w nakładających się okresach obowiązywania.
CREATE TABLE work_schedules (
    id          uuid        PRIMARY KEY,
    tenant_id   uuid        NOT NULL,
    employee_id uuid        NOT NULL REFERENCES employees (id),
    day_of_week smallint    NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time  time        NOT NULL,
    end_time    time        NOT NULL,
    valid_from  date        NOT NULL,
    valid_to    date,
    validity    daterange GENERATED ALWAYS AS (daterange(valid_from, valid_to, '[]')) STORED,
    span        tsrange GENERATED ALWAYS AS
                    (tsrange(date '2000-01-01' + start_time, date '2000-01-01' + end_time)) STORED,
    CHECK (end_time > start_time),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ex_work_schedules_overlap
        EXCLUDE USING gist (tenant_id WITH =, employee_id WITH =, day_of_week WITH =,
                            validity WITH &&, span WITH &&)
);
CREATE INDEX ix_work_schedules_employee ON work_schedules (tenant_id, employee_id);

CREATE TABLE schedule_exceptions (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    employee_id uuid NOT NULL REFERENCES employees (id),
    date        date NOT NULL,
    type        text NOT NULL CHECK (type IN ('ABSENCE', 'EXTRA_SHIFT')),
    start_time  time,
    end_time    time,
    CHECK (type = 'ABSENCE' OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time))
);
CREATE INDEX ix_schedule_exceptions_employee ON schedule_exceptions (tenant_id, employee_id, date);

ALTER TABLE employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE employees FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON employees
    USING (tenant_id = app_tenant_id()) WITH CHECK (tenant_id = app_tenant_id());

ALTER TABLE work_schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE work_schedules FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON work_schedules
    USING (tenant_id = app_tenant_id()) WITH CHECK (tenant_id = app_tenant_id());

ALTER TABLE schedule_exceptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE schedule_exceptions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON schedule_exceptions
    USING (tenant_id = app_tenant_id()) WITH CHECK (tenant_id = app_tenant_id());

