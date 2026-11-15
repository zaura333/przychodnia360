package pl.przychodnia360.common.security;

import pl.przychodnia360.ClinicCoreIntegrationTest;

/** Bramka z p360-test-support (PermissionMatrixContract); clinic-core nie wymaga dodatkowej konfiguracji. */
@ClinicCoreIntegrationTest
class PermissionMatrixTest extends PermissionMatrixContract {

    // po wydzieleniu katalogu i pacjentów clinic-core ma mniej endpointów niż domyślne progi bramki
    @Override
    protected int minimumEndpoints() {
        return 20;
    }

    @Override
    protected int minimumRoleEndpointPairs() {
        return 100;
    }
}
