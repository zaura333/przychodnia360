package pl.przychodnia360.common.web;

import pl.przychodnia360.PatientIntegrationTest;

/** Bramka z p360-test-support (HttpBehaviourContract): zachowanie HTTP usługi patient (bez listy placówek iam). */
@PatientIntegrationTest
class HttpBehaviourTest extends HttpBehaviourContract {

    @Override
    protected boolean listsFacilities() {
        return false;
    }
}
