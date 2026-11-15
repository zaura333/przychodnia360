package pl.przychodnia360;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Konfiguracja testowa iam (nagrywanie e-maili i zdarzeń, atrapa usługi pacjentów) dołączana do bazy testów i do
 * podklas kontraktów z p360-test-support. Identyczny zestaw importów we wszystkich testach daje jeden współdzielony
 * kontekst Springa (bez drugiego startu aplikacji).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import({RecordingMailSender.Config.class, RecordingEventTransport.Config.class, IamIntegrationTest.FakePatientsConfig.class})
public @interface IamIntegrationTest {

    /** Atrapa fasady pacjentów (@Primary przed zdalną implementacją). */
    @TestConfiguration
    class FakePatientsConfig {

        @Bean
        @Primary
        FakePatients fakePatients() {
            return new FakePatients();
        }
    }
}
