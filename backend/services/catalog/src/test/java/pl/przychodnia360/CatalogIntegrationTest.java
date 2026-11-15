package pl.przychodnia360;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

/**
 * Konfiguracja testowa catalog dołączana do bazy testów i do podklas kontraktów z p360-test-support. Identyczny
 * zestaw importów we wszystkich testach daje jeden współdzielony kontekst Springa (bez drugiego startu aplikacji).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import({RecordingEventTransport.Config.class})
@ContextConfiguration(initializers = TestTokenIssuer.JwksInitializer.class)
public @interface CatalogIntegrationTest {
}
