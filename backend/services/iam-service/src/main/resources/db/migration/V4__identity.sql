-- Tożsamość: użytkownicy i tokeny. Konta administratorów platformy mają tenant_id NULL.
CREATE FUNCTION app_scope() RETURNS text
    LANGUAGE sql STABLE
AS $$
    SELECT NULLIF(current_setting('app.scope', true), '')
$$;

CREATE TABLE users (
    id              uuid        PRIMARY KEY,
    tenant_id       uuid        REFERENCES tenants (id),
    email           citext      NOT NULL CHECK (length(btrim(email::text)) > 0),
    password_hash   text,
    role            text        NOT NULL CHECK (role IN
        ('PATIENT', 'DOCTOR', 'MEDICAL_STAFF', 'RECEPTIONIST', 'TENANT_ADMIN', 'PLATFORM_ADMIN')),
    status          text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED', 'DEACTIVATED')),
    failed_attempts int         NOT NULL DEFAULT 0,
    locked_until    timestamptz,
    -- powiązania ustawiane przez moduły staff i patients (bez FK: tabele powstają później, FK dodają ich migracje)
    employee_id     uuid,
    patient_id      uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint      NOT NULL DEFAULT 0,
    CHECK ((role = 'PLATFORM_ADMIN') = (tenant_id IS NULL))
);
-- password_hash NULL: konto utworzone przez administratora czeka na ustawienie hasła z linku.
CREATE UNIQUE INDEX ux_users_tenant_email ON users (tenant_id, email) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX ux_users_platform_email ON users (email) WHERE tenant_id IS NULL;

CREATE TABLE refresh_tokens (
    id         uuid        PRIMARY KEY,
    family_id  uuid        NOT NULL,
    user_id    uuid        NOT NULL REFERENCES users (id),
    tenant_id  uuid        REFERENCES tenants (id),
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (tenant_id, family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (tenant_id, user_id);

CREATE TABLE password_reset_tokens (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id),
    tenant_id  uuid        REFERENCES tenants (id),
    purpose    text        NOT NULL CHECK (purpose IN ('RESET', 'SET_PASSWORD')),
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_password_reset_user ON password_reset_tokens (tenant_id, user_id);

-- RLS: wiersze placówki widoczne w jej kontekście; wiersze platformy (tenant_id NULL) tylko w zakresie platformy.
-- Tylko te trzy tabele znają app_scope(), więc administrator platformy nie sięga do danych placówek.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_or_platform ON users
    USING (tenant_id = app_tenant_id() OR (tenant_id IS NULL AND app_scope() = 'platform'))
    WITH CHECK (tenant_id = app_tenant_id() OR (tenant_id IS NULL AND app_scope() = 'platform'));

ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_or_platform ON refresh_tokens
    USING (tenant_id = app_tenant_id() OR (tenant_id IS NULL AND app_scope() = 'platform'))
    WITH CHECK (tenant_id = app_tenant_id() OR (tenant_id IS NULL AND app_scope() = 'platform'));

ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_or_platform ON password_reset_tokens
    USING (tenant_id = app_tenant_id() OR (tenant_id IS NULL AND app_scope() = 'platform'))
    WITH CHECK (tenant_id = app_tenant_id() OR (tenant_id IS NULL AND app_scope() = 'platform'));

-- Konto administratora placówki tworzy administrator platformy, który nie ma kontekstu placówki.
-- Funkcja SECURITY DEFINER wstawia wyłącznie rolę TENANT_ADMIN w wybranej placówce i przywraca kontekst.
CREATE FUNCTION platform_create_tenant_admin(p_id uuid, p_tenant_id uuid, p_email text, p_password_hash text)
    RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    previous text := current_setting('app.tenant_id', true);
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tenants WHERE id = p_tenant_id) THEN
        RAISE EXCEPTION 'unknown tenant' USING ERRCODE = 'P0002';
    END IF;
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);
    INSERT INTO users (id, tenant_id, email, password_hash, role)
    VALUES (p_id, p_tenant_id, p_email, p_password_hash, 'TENANT_ADMIN');
    PERFORM set_config('app.tenant_id', COALESCE(previous, ''), true);
    RETURN p_id;
END
$$;

REVOKE ALL ON FUNCTION platform_create_tenant_admin(uuid, uuid, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform_create_tenant_admin(uuid, uuid, text, text) TO p360_app;
