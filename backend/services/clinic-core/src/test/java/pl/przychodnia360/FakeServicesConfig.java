package pl.przychodnia360;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pl.przychodnia360.platform.FakeSeedCatalog;

/**
 * Atrapy fasad catalog i patient (@Primary przed zdalnymi implementacjami) dla testów clinic-core. Jeden zestaw w
 * kontekście wspólnym dla wszystkich testów (importowany przez {@link ClinicCoreIntegrationTest}).
 */
@TestConfiguration
public class FakeServicesConfig {

    @Bean
    @Primary
    FakeCatalog fakeCatalog() {
        return new FakeCatalog();
    }

    @Bean
    @Primary
    FakePatients fakePatients() {
        return new FakePatients();
    }

    @Bean
    @Primary
    FakeSeedCatalog fakeSeedCatalog(FakeCatalog catalog) {
        return new FakeSeedCatalog(catalog);
    }
}
