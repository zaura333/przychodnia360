package pl.przychodnia360.common.security;

import pl.przychodnia360.PatientIntegrationTest;

/** Bramka z p360-test-support (PermissionMatrixContract) dla małej usługi: kilka endpointów, nie ponad 30. */
@PatientIntegrationTest
class PermissionMatrixTest extends PermissionMatrixContract {

    @Override
    protected int minimumEndpoints() {
        return 5;
    }

    @Override
    protected int minimumRoleEndpointPairs() {
        return 20;
    }

    /** SecurityConfig ma wspólną białą listę (ścieżki iam), a patient implementuje tylko sondy zdrowia. */
    @Override
    protected java.util.Set<String> publicPaths() {
        return java.util.Set.of("/api/v1/health", "/api/v1/health/db");
    }
}
