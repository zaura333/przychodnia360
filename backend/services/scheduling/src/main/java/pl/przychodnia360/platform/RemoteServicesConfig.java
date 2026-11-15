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
import pl.przychodnia360.common.http.ServiceTokenClient;
import pl.przychodnia360.shared.TenantContext;

/**
 * Klienty do pozostałych usług (iam, catalog, patient) z adresami {@code p360.<usługa>.url}; limity czasu i wyłącznik
 * obwodu są konfigurowalne ({@code p360.<usługa>.connect-timeout|read-timeout|failure-threshold|open-for}). Tożsamość
 * usługi: tokeny usługowe pobierane od iam za sekretem tej usługi (ADR-M17), po jednym na placówkę i usługę docelową.
 */
@Configuration
class RemoteServicesConfig {

    @Bean
    ServiceTokenClient serviceTokenClient(@Value("${p360.iam.url}") String iamUrl,
            @Value("${spring.application.name}") String serviceName,
            @Value("${p360.service-client.secret:}") String secret,
            @Value("${p360.iam.read-timeout:PT2S}") Duration timeout, Clock clock,
            ObjectProvider<RestClient.Builder> builders) {
        return new ServiceTokenClient(iamUrl, serviceName, secret, timeout, builders.getIfAvailable(RestClient::builder),
                clock);
    }

    private static RemoteServiceClient build(String name, String url, Duration connect, Duration read, int threshold,
            Duration openFor, Clock clock, ObjectProvider<RestClient.Builder> builders,
            ObjectProvider<ObservationRegistry> registry, ServiceTokenClient tokens) {
        RestClient.Builder builder = builders.getIfAvailable(RestClient::builder);
        registry.ifAvailable(builder::observationRegistry); // ślad (traceparent) przechodzi do wywoływanej usługi
        // token usługowy dla bieżącej placówki i tej usługi docelowej (bez kontekstu placówki: null)
        Supplier<String> serviceToken = () -> TenantContext.current().map(t -> tokens.token(t.value(), name)).orElse(null);
        return new RemoteServiceClient(name, url, connect, read, new CircuitBreaker(threshold, openFor, clock), builder,
                serviceToken);
    }

    @Bean
    RemoteServiceClient iamClient(@Value("${p360.iam.url}") String url,
            @Value("${p360.iam.connect-timeout:PT0.5S}") Duration connect,
            @Value("${p360.iam.read-timeout:PT2S}") Duration read,
            @Value("${p360.iam.failure-threshold:5}") int threshold,
            @Value("${p360.iam.open-for:PT10S}") Duration openFor, Clock clock,
            ObjectProvider<RestClient.Builder> builders, ObjectProvider<ObservationRegistry> registry,
            ServiceTokenClient tokens) {
        return build("iam", url, connect, read, threshold, openFor, clock, builders, registry, tokens);
    }

    @Bean
    RemoteServiceClient catalogClient(@Value("${p360.catalog.url}") String url,
            @Value("${p360.catalog.connect-timeout:PT0.5S}") Duration connect,
            @Value("${p360.catalog.read-timeout:PT2S}") Duration read,
            @Value("${p360.catalog.failure-threshold:5}") int threshold,
            @Value("${p360.catalog.open-for:PT10S}") Duration openFor, Clock clock,
            ObjectProvider<RestClient.Builder> builders, ObjectProvider<ObservationRegistry> registry,
            ServiceTokenClient tokens) {
        return build("catalog", url, connect, read, threshold, openFor, clock, builders, registry, tokens);
    }

    @Bean
    RemoteServiceClient patientClient(@Value("${p360.patient.url}") String url,
            @Value("${p360.patient.connect-timeout:PT0.5S}") Duration connect,
            @Value("${p360.patient.read-timeout:PT2S}") Duration read,
            @Value("${p360.patient.failure-threshold:5}") int threshold,
            @Value("${p360.patient.open-for:PT10S}") Duration openFor, Clock clock,
            ObjectProvider<RestClient.Builder> builders, ObjectProvider<ObservationRegistry> registry,
            ServiceTokenClient tokens) {
        return build("patient", url, connect, read, threshold, openFor, clock, builders, registry, tokens);
    }
}
