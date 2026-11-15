package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.FakePatients.Failure;
import pl.przychodnia360.identity.application.RegistrationService;
import pl.przychodnia360.identity.application.RegistrationService.Registration;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.shared.ServiceUnavailableException;

/**
 * Saga rejestracji pacjenta (S2): awaria usługi pacjentów nie zostawia konta bez pacjenta (kompensacja przez wycofanie
 * konta), a powtórzenie po awarii, także niejednoznacznej (rekord powstał, odpowiedź nie doszła), kończy się jednym
 * pacjentem powiązanym z jednym kontem, bo identyfikator pacjenta wynika z placówki i e-maila. Atrapa usługi pacjentów
 * zatwierdza zapis niezależnie od transakcji konta, jak prawdziwa usługa zdalna.
 */
class RegistrationSagaTest extends AbstractApiTest {

    @Autowired RegistrationService registration;

    @AfterEach
    void recover() {
        fakePatients.reset();
    }

    private Registration registration(UUID tenant, String email) {
        return new Registration(slugOf(tenant), email, "correct horse battery", "Ewa", "Zielińska", LocalDate.of(1990, 4, 5));
    }

    private Optional<User> account(UUID tenant, String email) {
        return inTenantResult(tenant, () -> users.findByEmail(tenant, email));
    }

    @Test
    void successfulRegistrationLinksAccountAndPatientInBothDirections() {
        UUID tenant = newTenant();
        String email = "ewa." + unique() + "@example.test";

        registration.registerPatient(registration(tenant, email));

        User user = account(tenant, email).orElseThrow();
        assertThat(user.patientId()).isNotNull();
        var patient = fakePatients.find(tenant, user.patientId()).orElseThrow();
        assertThat(patient.userId()).isEqualTo(user.id());
        assertThat(patient.fullName()).isEqualTo("Ewa Zielińska");
    }

    @Test
    void patientServiceOutageLeavesNoAccountAndRetryCompletesTheRegistration() {
        UUID tenant = newTenant();
        String email = "awaria." + unique() + "@example.test";
        fakePatients.failWith(Failure.BEFORE);

        assertThatThrownBy(() -> registration.registerPatient(registration(tenant, email)))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(account(tenant, email)).as("konto cofnięte").isEmpty();
        assertThat(fakePatients.countWithEmail(tenant, email)).isZero();

        fakePatients.reset();
        registration.registerPatient(registration(tenant, email));
        assertThat(account(tenant, email)).isPresent();
        assertThat(fakePatients.countWithEmail(tenant, email)).isEqualTo(1);
    }

    @Test
    void lostResponseAfterTheWriteStillEndsWithOnePatientAndOneAccount() {
        UUID tenant = newTenant();
        String email = "zgubiona." + unique() + "@example.test";
        fakePatients.failWith(Failure.AFTER);

        assertThatThrownBy(() -> registration.registerPatient(registration(tenant, email)))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(account(tenant, email)).as("konto cofnięte mimo zapisu po stronie pacjentów").isEmpty();
        assertThat(fakePatients.countWithEmail(tenant, email)).as("rekord pacjenta został").isEqualTo(1);

        fakePatients.reset();
        registration.registerPatient(registration(tenant, email));

        User user = account(tenant, email).orElseThrow();
        assertThat(fakePatients.countWithEmail(tenant, email)).as("ten sam identyfikator, bez duplikatu").isEqualTo(1);
        assertThat(fakePatients.find(tenant, user.patientId()).orElseThrow().userId())
                .as("rekord uzupełniony powiązaniem z kontem").isEqualTo(user.id());
    }

    @Test
    void takenEmailEndsBeforeAnyCallToThePatientService() {
        UUID tenant = newTenant();
        String email = "zajety." + unique() + "@example.test";
        registration.registerPatient(registration(tenant, email));
        fakePatients.failWith(Failure.BEFORE);

        registration.registerPatient(registration(tenant, email));

        assertThat(fakePatients.countWithEmail(tenant, email)).as("brak drugiego rekordu i brak wyjątku z usługi pacjentów")
                .isEqualTo(1);
    }
}
