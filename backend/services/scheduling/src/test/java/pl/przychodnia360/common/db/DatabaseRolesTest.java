package pl.przychodnia360.common.db;

import pl.przychodnia360.SchedulingIntegrationTest;

/** Bramka z p360-test-support (DatabaseRolesContract); placówki i resolve_tenant należą do iam (S3). */
@SchedulingIntegrationTest
class DatabaseRolesTest extends DatabaseRolesContract {

    @Override
    protected boolean hasTenantResolver() {
        return false; // tabela placówek i resolve_tenant należą do iam
    }
}
