package pl.przychodnia360.common.db;

import pl.przychodnia360.PatientIntegrationTest;

/** Bramka z p360-test-support (TenantContextLeakContract): kontekst placówki nie przecieka między żądaniami. */
@PatientIntegrationTest
class TenantContextLeakTest extends TenantContextLeakContract {
}
