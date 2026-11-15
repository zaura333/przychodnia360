-- S1, S2 (krok 9 wzorca wycinania): katalog (service_categories, services) i pacjenci (patients) należą do usług
-- catalog i patient (własne bazy p360_catalog i p360_patient). Tabele w tej bazie są martwe, więc znikają.
--
-- Zabezpieczenie: migracja NIE usuwa tabel, które zawierają wiersze, bo w tym środowisku mogą być dane, których nie
-- skopiowano do baz usług. Wtedy najpierw uruchom docker/scripts/copy-extracted-data.sh (kopiuje dane do baz usług
-- i sprawdza liczności), albo zresetuj środowisko deweloperskie (docker compose down -v; dane demo powstają z seedu).
-- Polityki RLS tabel znikają razem z nimi (DROP TABLE); tabele platformowe (outbox, processed_events) zostają.
DO $$
DECLARE
    t text;
    n bigint;
BEGIN
    FOREACH t IN ARRAY ARRAY['services', 'service_categories', 'patients'] LOOP
        IF to_regclass('public.' || t) IS NOT NULL THEN
            -- właściciel podlega FORCE RLS i bez kontekstu placówki widziałby 0 wierszy; zdjęcie FORCE działa tylko w tej
            -- transakcji (wyjątek poniżej ją wycofuje, więc tabela zostaje w stanie sprzed migracji)
            EXECUTE format('ALTER TABLE %I NO FORCE ROW LEVEL SECURITY', t);
            EXECUTE format('SELECT count(*) FROM %I', t) INTO n;
            IF n > 0 THEN
                RAISE EXCEPTION 'Tabela % zawiera % wierszy, a modul zostal wydzielony do osobnej uslugi. '
                    'Skopiuj dane skryptem docker/scripts/copy-extracted-data.sh albo zresetuj srodowisko (docker compose down -v).', t, n;
            END IF;
        END IF;
    END LOOP;
END
$$;

DROP TABLE IF EXISTS services;
DROP TABLE IF EXISTS service_categories;
DROP TABLE IF EXISTS patients;
