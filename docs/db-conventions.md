# Konwencje bazy danych

- Migracje: `backend/services/clinic-core/src/main/resources/db/migration`, nazwy `V<n>__opis.sql`. Skrypty są niezmienne po scaleniu; zmiany zgodne wstecz (rozszerzaj, przełącz, usuń w osobnym wydaniu).
- Klucze główne `uuid` (`gen_random_uuid()`), znaczniki czasu `timestamptz`.
- Każda tabela danych placówki ma `tenant_id`, `ENABLE` i `FORCE ROW LEVEL SECURITY` oraz politykę `USING/WITH CHECK (tenant_id = app_tenant_id())`. Wzorzec: `backend/services/clinic-core/src/test/resources/db/testmigration/V1000__tenant_probe.sql`. Bramka: `RlsCoverageTest`.
- Tabela bez `tenant_id` wymaga wpisu na liście wyjątków w `RlsCoverageTest` z uzasadnieniem.
- Role: `p360_owner` (migracje, tworzona przez `docker/postgres/init`) i `p360_app` (aplikacja, tworzona w V1; bez BYPASSRLS i bez DDL).
- Kontekst placówki: `TenantContext` w `platform/p360-shared-core`, wiązany z transakcją przez `TenantAwareTransactionManager` (`set_config(..., true)`). Transakcja bez kontekstu jest odrzucana; wyjątki jawnie przez `TenantContext.openWithoutTenant()`.
