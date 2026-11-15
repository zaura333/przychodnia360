package pl.przychodnia360.platform;

import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import pl.przychodnia360.common.http.CircuitBreaker;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.identity.infrastructure.JwtService;
import pl.przychodnia360.shared.TenantContext;

/**
 * Klient usługi pacjentów (saga rejestracji pacjenta, S2) z adresem {@code p360.patient.url}; limity czasu i wyłącznik
 * obwodu konfigurowalne ({@code p360.patient.connect-timeout|read-timeout|failure-threshold|open-for}). iam ma klucz
 * prywatny, więc token usługowy wystawia sobie sam.
 */
@Configuration
class RemoteServicesConfig {

    @Bean
    RemoteServiceClient patientClient(@Value("${p360.patient.url}") String url,
            @Value("${p360.patient.connect-timeout:PT0.5S}") Duration connect,
            @Value("${p360.patient.read-timeout:PT2S}") Duration read,
            @Value("${p360.patient.failure-threshold:5}") int threshold,
            @Value("${p360.patient.open-for:PT10S}") Duration openFor, Clock clock,
            ObjectProvider<RestClient.Builder> builders, ObjectProvider<ObservationRegistry> registry,
            JwtService jwt) {
        RestClient.Builder builder = builders.getIfAvailable(RestClient::builder);
        registry.ifAvailable(builder::observationRegistry); // ślad (traceparent) przechodzi do wywoływanej usługi
        // token usługowy iam dla bieżącej placówki (bez kontekstu placówki: null)
        Supplier<String> serviceToken = () -> TenantContext.current()
                .map(t -> jwt.issueService(t.value(), "patient")).orElse(null);
        return new RemoteServiceClient("patient", url, connect, read, new CircuitBreaker(threshold, openFor, clock),
                builder, serviceToken);
    }
}
