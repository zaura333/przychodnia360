-- Fala 0R (ADR-M05): moduły (przyszłe usługi) nie mają kluczy obcych między sobą. Powiązania zostają jako
-- identyfikatory, a spójność pilnują fasady modułów i test ModuleBoundariesFkTest. Zmiana zgodna wstecz
-- (usuwa tylko ograniczenia). Klucze wewnątrz modułu (np. appointments -> employees, services -> service_categories,
-- tokeny -> users) zostają.
--
-- Moduły: iam (tenants, users, refresh_tokens, password_reset_tokens), catalog (service_categories, services),
-- patients (patients), scheduling (employees, work_schedules, schedule_exceptions, appointments).
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.conrelid::regclass::text AS tbl, c.conname
          FROM pg_constraint c
         WHERE c.contype = 'f'
           AND (c.conrelid::regclass::text, c.confrelid::regclass::text) IN (
               -- cykl konto <-> podmiot
               ('users', 'patients'), ('users', 'employees'), ('patients', 'users'), ('employees', 'users'),
               -- wizyty -> pacjenci, usługi, konta
               ('appointments', 'patients'), ('appointments', 'services'), ('appointments', 'users'),
               -- placówka jest słownikiem w iam; pozostałe moduły trzymają tylko tenant_id (plus RLS)
               ('service_categories', 'tenants'), ('services', 'tenants'), ('patients', 'tenants'),
               ('employees', 'tenants'), ('work_schedules', 'tenants'), ('schedule_exceptions', 'tenants'),
               ('appointments', 'tenants'))
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END $$;
