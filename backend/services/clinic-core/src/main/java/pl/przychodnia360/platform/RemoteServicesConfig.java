package pl.przychodnia360.platform;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import pl.przychodnia360.identity.infrastructure.JwtService;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.common.http.CircuitBreaker;
import pl.przychodnia360.common.http.RemoteServiceClient;

/**
 * Klienty do wydzielonych usług (S1, S2) z adresami {@code p360.<usługa>.url};
 * limity czasu i wyłącznik obwodu są konfigurowalne ({@code p360.<usługa>.connect-timeout|read-timeout|
 * failure-threshold|open-for}).
 */
@Configuration
class RemoteServicesConfig {

    private static RemoteServiceClient build(String name, String url, Duration connect, Duration read, int threshold,
            Duration openFor, Clock clock, ObjectProvider<RestClient.Builder> builders,
            ObjectProvider<ObservationRegistry> registry, JwtService jwt) {
        RestClient.Builder builder = builders.getIfAvailable(RestClient::builder);
        registry.ifAvailable(builder::observationRegistry); // ślad (traceparent) przechodzi do wywoływanej usługi
        // tożsamość usługi: token usługowy iam dla bieżącej placówki i tej usługi docelowej (bez kontekstu placówki: null)
        Supplier<String> serviceToken = () -> TenantContext.current().map(t -> jwt.issueService(t.value(), name)).orElse(null);
        return new RemoteServiceClient(name, url, connect, read, new CircuitBreaker(threshold, openFor, clock), builder,
                serviceToken);
    }

    @Bean
    RemoteServiceClient catalogClient(@Value("${p360.catalog.url}") String url,
            @Value("${p360.catalog.connect-timeout:PT0.5S}") Duration connect,
            @Value("${p360.catalog.read-timeout:PT2S}") Duration read,
            @Value("${p360.catalog.failure-threshold:5}") int threshold,
            @Value("${p360.catalog.open-for:PT10S}") Duration openFor, Clock clock,
            ObjectProvider<RestClient.Builder> builders, ObjectProvider<ObservationRegistry> registry,
            JwtService jwt) {
        return build("catalog", url, connect, read, threshold, openFor, clock, builders, registry, jwt);
    }

    @Bean
    RemoteServiceClient patientClient(@Value("${p360.patient.url}") String url,
            @Value("${p360.patient.connect-timeout:PT0.5S}") Duration connect,
            @Value("${p360.patient.read-timeout:PT2S}") Duration read,
            @Value("${p360.patient.failure-threshold:5}") int threshold,
            @Value("${p360.patient.open-for:PT10S}") Duration openFor, Clock clock,
            ObjectProvider<RestClient.Builder> builders, ObjectProvider<ObservationRegistry> registry,
            JwtService jwt) {
        return build("patient", url, connect, read, threshold, openFor, clock, builders, registry, jwt);
    }
}
