package pl.przychodnia360;

import pl.przychodnia360.testsupport.PostgresIntegrationTest;

/** Baza testów clinic-core: wspólna baza z p360-test-support plus nagrywanie e-maili. */
@ClinicCoreIntegrationTest
public abstract class AbstractIntegrationTest extends PostgresIntegrationTest {
}
