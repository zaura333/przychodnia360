package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.identity.application.RegistrationService;
import pl.przychodnia360.identity.application.RegistrationService.Registration;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.patients.application.PatientService;
import pl.przychodnia360.shared.ServiceUnavailableException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saga rejestracji pacjenta (S2): awaria usługi pacjentów nie zostawia konta bez pacjenta (kompensacja przez wycofanie
 * konta), a powtórzenie po awarii, także niejednoznacznej (rekord powstał, odpowiedź nie doszła), kończy się jednym
 * pacjentem powiązanym z jednym kontem, bo identyfikator pacjenta wynika z placówki i e-maila.
 */
@Import(RegistrationSagaTest.FlakyPatients.class)
class RegistrationSagaTest extends AbstractApiTest {

    /** Failure: przed zapisem albo po zapisie (odpowiedź zgubiona). */
    enum Failure { NONE, BEFORE, AFTER }

    static final AtomicReference<Failure> FAILURE = new AtomicReference<>(Failure.NONE);

    @TestConfiguration
    static class FlakyPatients {
        @Bean
        @Primary
        PatientFacade flakyPatientFacade(PatientService real, PlatformTransactionManager txManager) {
            // zdalna usługa zatwierdza zapis niezależnie od transakcji konta: REQUIRES_NEW odtwarza tę właściwość w jednej bazie
            TransactionTemplate remoteCommit = new TransactionTemplate(txManager);
            remoteCommit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return new PatientFacade() {
                @Override
                public UUID create(String f, String l, LocalDate b, String e) {
                    return real.create(f, l, b, e);
                }

                @Override
                public void ensure(UUID id, String f, String l, LocalDate b, String e, UUID userId) {
                    if (FAILURE.get() == Failure.BEFORE) {
                        throw new ServiceUnavailableException("patient niedostępny");
                    }
                    remoteCommit.executeWithoutResult(status -> real.ensure(id, f, l, b, e, userId));
                    if (FAILURE.get() == Failure.AFTER) {
                        throw new ServiceUnavailableException("patient nie odpowiada");
                    }
                }

                @Override
                public void linkAccount(UUID p, UUID u) {
                    real.linkAccount(p, u);
                }

                @Override
                public Optional<PatientInfo> find(UUID id) {
                    return real.find(id);
                }

                @Override
                public Map<UUID, PatientInfo> findAll(Collection<UUID> ids) {
                    return real.findAll(ids);
                }
            };
        }
    }

    @Autowired RegistrationService registration;
    @Autowired PatientService patientService;

    @AfterEach
    void recover() {
        FAILURE.set(Failure.NONE);
    }

    private Registration registration(UUID tenant, String email) {
        return new Registration(slugOf(tenant), email, "correct horse battery", "Ewa", "Zielińska", LocalDate.of(1990, 4, 5));
    }

    private int patientsWithEmail(UUID tenant, String email) {
        return inTenantResult(tenant, () -> jdbc.sql("SELECT count(*) FROM patients WHERE contact_email = ?")
                .param(email).query(Integer.class).single());
    }

    private Optional<pl.przychodnia360.identity.domain.User> account(UUID tenant, String email) {
        return inTenantResult(tenant, () -> users.findByEmail(tenant, email));
    }

    @Test
    void successfulRegistrationLinksAccountAndPatientInBothDirections() {
        UUID tenant = newTenant();
        String email = "ewa." + unique() + "@example.test";

        registration.registerPatient(registration(tenant, email));

        var user = account(tenant, email).orElseThrow();
        assertThat(user.patientId()).isNotNull();
        var patient = inTenantResult(tenant, () -> patientService.find(user.patientId())).orElseThrow();
        assertThat(patient.userId()).isEqualTo(user.id());
        assertThat(patient.fullName()).isEqualTo("Ewa Zielińska");
    }

    @Test
    void patientServiceOutageLeavesNoAccountAndRetryCompletesTheRegistration() {
        UUID tenant = newTenant();
        String email = "awaria." + unique() + "@example.test";
        FAILURE.set(Failure.BEFORE);

        assertThatThrownBy(() -> registration.registerPatient(registration(tenant, email)))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(account(tenant, email)).as("konto cofnięte").isEmpty();
        assertThat(patientsWithEmail(tenant, email)).isZero();

        FAILURE.set(Failure.NONE);
        registration.registerPatient(registration(tenant, email));
        assertThat(account(tenant, email)).isPresent();
        assertThat(patientsWithEmail(tenant, email)).isEqualTo(1);
    }

    @Test
    void lostResponseAfterTheWriteStillEndsWithOnePatientAndOneAccount() {
        UUID tenant = newTenant();
        String email = "zgubiona." + unique() + "@example.test";
        FAILURE.set(Failure.AFTER);

        assertThatThrownBy(() -> registration.registerPatient(registration(tenant, email)))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(account(tenant, email)).as("konto cofnięte mimo zapisu po stronie pacjentów").isEmpty();
        assertThat(patientsWithEmail(tenant, email)).as("rekord pacjenta został").isEqualTo(1);

        FAILURE.set(Failure.NONE);
        registration.registerPatient(registration(tenant, email));

        var user = account(tenant, email).orElseThrow();
        assertThat(patientsWithEmail(tenant, email)).as("ten sam identyfikator, bez duplikatu").isEqualTo(1);
        assertThat(inTenantResult(tenant, () -> patientService.find(user.patientId())).orElseThrow().userId())
                .as("rekord uzupełniony powiązaniem z kontem").isEqualTo(user.id());
    }

    @Test
    void takenEmailEndsBeforeAnyCallToThePatientService() {
        UUID tenant = newTenant();
        String email = "zajety." + unique() + "@example.test";
        registration.registerPatient(registration(tenant, email));
        FAILURE.set(Failure.BEFORE);

        registration.registerPatient(registration(tenant, email));

        assertThat(patientsWithEmail(tenant, email)).as("brak drugiego rekordu i brak wyjątku z usługi pacjentów").isEqualTo(1);
    }
}
