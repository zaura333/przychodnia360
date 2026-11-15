package pl.przychodnia360;

import pl.przychodnia360.testsupport.PostgresIntegrationTest;

/** Baza testów scheduling: wspólna baza z p360-test-support (baza p360 i role p360_owner/p360_app, ADR-M12). */
@SchedulingIntegrationTest
public abstract class AbstractIntegrationTest extends PostgresIntegrationTest {
}
