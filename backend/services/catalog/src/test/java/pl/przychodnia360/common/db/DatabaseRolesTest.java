package pl.przychodnia360.common.db;

import pl.przychodnia360.CatalogIntegrationTest;

/** Bramka ról z p360-test-support: usługa łączy się jako p360_catalog_app, właściciel to p360_catalog_owner. */
@CatalogIntegrationTest
class DatabaseRolesTest extends DatabaseRolesContract {

    @Override
    protected boolean hasTenantResolver() {
        return false; // tabela placówek i resolve_tenant należą do iam
    }
}
