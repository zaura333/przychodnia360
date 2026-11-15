package pl.przychodnia360.common.security;

import pl.przychodnia360.SchedulingIntegrationTest;

/** Bramka z p360-test-support (PermissionMatrixContract); scheduling nie wymaga dodatkowej konfiguracji. */
@SchedulingIntegrationTest
class PermissionMatrixTest extends PermissionMatrixContract {

    // jedyne publiczne ścieżki scheduling (logowanie, placówki i JWKS należą do iam)
    @Override
    protected java.util.Set<String> publicPaths() {
        return java.util.Set.of("/api/v1/health", "/api/v1/health/db");
    }

    // po wydzieleniu katalogu, pacjentów i kont (S1–S3) scheduling ma mniej endpointów niż domyślne progi bramki
    @Override
    protected int minimumEndpoints() {
        return 12;
    }

    @Override
    protected int minimumRoleEndpointPairs() {
        return 60;
    }
}
