package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.api.IdentityFacade;
import pl.przychodnia360.identity.domain.User;

/** 0R.8 (ADR-M04): dezaktywacja konta odcina dostęp od razu, bez czekania na wygaśnięcie tokenu dostępu. */
class DeactivationRevokesAccessTest extends AbstractApiTest {

    @Autowired
    IdentityFacade identity;

    @Test
    void deactivatedUserLosesAccessImmediately() {
        UUID tenant = newTenant();
        User doctor = newUser(tenant, Role.DOCTOR);
        String token = login(tenant, doctor);
        assertThat(call("GET", "/api/v1/me", token, null).status()).isEqualTo(200);

        inTenant(tenant, () -> identity.deactivate(doctor.id()));

        assertThat(call("GET", "/api/v1/me", token, null).status()).isEqualTo(401);
    }

    @Test
    void otherUsersOfTheSameTenantKeepAccess() {
        UUID tenant = newTenant();
        User gone = newUser(tenant, Role.DOCTOR);
        User stays = newUser(tenant, Role.RECEPTIONIST);
        String stayToken = login(tenant, stays);

        inTenant(tenant, () -> identity.deactivate(gone.id()));

        assertThat(call("GET", "/api/v1/me", stayToken, null).status()).isEqualTo(200);
    }
}
