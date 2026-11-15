package pl.przychodnia360.common.security;

import pl.przychodnia360.IamIntegrationTest;

/** Bramka z p360-test-support (PermissionMatrixContract); clinic-core nie wymaga dodatkowej konfiguracji. */
@IamIntegrationTest
class PermissionMatrixTest extends PermissionMatrixContract {

    // iam ma mniej endpointów niż domyślne progi bramki (sama tożsamość i placówki)
    @Override
    protected int minimumEndpoints() {
        return 15;
    }

    @Override
    protected int minimumRoleEndpointPairs() {
        return 50;
    }
}
