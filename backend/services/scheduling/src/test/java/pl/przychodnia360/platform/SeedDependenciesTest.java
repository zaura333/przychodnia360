package pl.przychodnia360.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.catalog.api.CatalogFacade;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.shared.ServiceUnavailableException;

/** Seed czeka na usługi catalog i patient przed utworzeniem stanu (zimny start, awaria na starcie) i nie czeka w nieskończoność. */
class SeedDependenciesTest {

    /** Katalog niedostępny przez kilka pierwszych wywołań, potem odpowiada. */
    private static final class FlakyCatalog implements CatalogFacade {
        final AtomicInteger calls = new AtomicInteger();
        final int failures;

        FlakyCatalog(int failures) {
            this.failures = failures;
        }

        @Override
        public Optional<ServiceInfo> service(UUID serviceId) {
            if (calls.incrementAndGet() <= failures) {
                throw new ServiceUnavailableException("catalog jeszcze nie odpowiada");
            }
            return Optional.empty();
        }

        @Override
        public Map<UUID, ServiceInfo> services(Collection<UUID> ids) {
            return Map.of();
        }
    }

    private static final class QuietPatients implements PatientFacade {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public UUID create(String f, String l, LocalDate b, String e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ensure(UUID id, String f, String l, LocalDate b, String e, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void linkAccount(UUID p, UUID u) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PatientInfo> find(UUID patientId) {
            calls.incrementAndGet();
            return Optional.empty();
        }

        @Override
        public Map<UUID, PatientInfo> findAll(Collection<UUID> ids) {
            return Map.of();
        }
    }

    @Test
    void retriesUntilTheServicesRespondAndOnlyThenReturns() {
        FlakyCatalog catalog = new FlakyCatalog(3);
        QuietPatients patients = new QuietPatients();

        new SeedDependencies(catalog, patients, 10, Duration.ofMillis(5)).await(java.util.UUID.randomUUID());

        assertThat(catalog.calls.get()).as("3 porażki i 1 sukces").isEqualTo(4);
        assertThat(patients.calls.get()).as("pacjenci sprawdzani dopiero po katalogu").isEqualTo(1);
    }

    @Test
    void givesUpWithAClearMessageWhenTheServicesNeverRespond() {
        FlakyCatalog catalog = new FlakyCatalog(Integer.MAX_VALUE);

        assertThatThrownBy(() -> new SeedDependencies(catalog, new QuietPatients(), 3, Duration.ofMillis(1)).await(java.util.UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("przed utworzeniem danych")
                .hasMessageContaining("3 próbach").hasCauseInstanceOf(ServiceUnavailableException.class);
        assertThat(catalog.calls.get()).isEqualTo(3);
    }
}
