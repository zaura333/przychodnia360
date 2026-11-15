package pl.przychodnia360.common.web;

import pl.przychodnia360.ClinicCoreIntegrationTest;

/** Bramka z p360-test-support (HttpBehaviourContract): scheduling bez listy placówek (należy do iam, S3). */
@ClinicCoreIntegrationTest
class HttpBehaviourTest extends HttpBehaviourContract {

    @Override
    protected boolean listsFacilities() {
        return false;
    }
}
