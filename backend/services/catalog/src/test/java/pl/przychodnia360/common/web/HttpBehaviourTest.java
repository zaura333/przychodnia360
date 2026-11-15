package pl.przychodnia360.common.web;

import pl.przychodnia360.CatalogIntegrationTest;

/** Bramka z p360-test-support (HttpBehaviourContract): zachowanie HTTP usługi catalog (bez listy placówek iam). */
@CatalogIntegrationTest
class HttpBehaviourTest extends HttpBehaviourContract {

    @Override
    protected boolean listsFacilities() {
        return false;
    }
}
