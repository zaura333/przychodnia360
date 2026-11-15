-- S3: placówki (tenants) i konta (users, refresh_tokens, password_reset_tokens) należą do usługi iam (własna baza
-- p360_iam). Tabele w tej bazie są martwe, więc znikają razem z funkcjami, które ich używały. RLS pozostałych tabel
-- opiera się wyłącznie na app_tenant_id(), który zostaje.
--
-- Zabezpieczenie jak w V12: migracja NIE usuwa tabel, które zawierają wiersze. Wtedy skopiuj dane skryptem
-- docker/scripts/copy-extracted-data.sh (iam uruchomiony pierwszy raz z SEED_ENABLED=false) albo zresetuj środowisko
-- deweloperskie (docker compose down -v; dane demo powstają z seedu iam i scheduling).
DO $$
DECLARE
    t text;
    n bigint;
BEGIN
    FOREACH t IN ARRAY ARRAY['password_reset_tokens', 'refresh_tokens', 'users', 'tenants'] LOOP
        IF to_regclass('public.' || t) IS NOT NULL THEN
            -- właściciel podlega FORCE RLS i bez kontekstu widziałby 0 wierszy; zdjęcie FORCE działa tylko w tej
            -- transakcji (wyjątek poniżej ją wycofuje, więc tabela zostaje w stanie sprzed migracji)
            EXECUTE format('ALTER TABLE %I NO FORCE ROW LEVEL SECURITY', t);
            EXECUTE format('SELECT count(*) FROM %I', t) INTO n;
            IF n > 0 THEN
                RAISE EXCEPTION 'Tabela % zawiera % wierszy, a konta i placowki naleza do uslugi iam. '
                    'Skopiuj dane skryptem docker/scripts/copy-extracted-data.sh albo zresetuj srodowisko (docker compose down -v).', t, n;
            END IF;
        END IF;
    END LOOP;
END
$$;

DROP FUNCTION IF EXISTS platform_create_tenant_admin(uuid, uuid, text, text);
DROP FUNCTION IF EXISTS resolve_tenant(text);
DROP TABLE IF EXISTS password_reset_tokens;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS users;
DROP FUNCTION IF EXISTS app_scope();
DROP TABLE IF EXISTS tenants;
