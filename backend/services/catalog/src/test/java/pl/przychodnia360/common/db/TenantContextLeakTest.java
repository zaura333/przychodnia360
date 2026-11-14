package pl.przychodnia360.common.db;

import pl.przychodnia360.CatalogIntegrationTest;

/** Bramka z p360-test-support (TenantContextLeakContract): kontekst placówki nie przecieka między żądaniami. */
@CatalogIntegrationTest
class TenantContextLeakTest extends TenantContextLeakContract {
}
