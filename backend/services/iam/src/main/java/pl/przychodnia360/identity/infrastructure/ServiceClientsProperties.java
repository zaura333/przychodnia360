package pl.przychodnia360.identity.infrastructure;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Usługi platformy, które mogą pobierać od iam tokeny usługowe ({@code p360.service-clients.<nazwa>.*}): sekret
 * (z sekretów środowiska, nigdy w repozytorium) i lista usług docelowych ({@code aud}), do których dana usługa może
 * wołać. Brak wpisów oznacza, że żadna usługa nie dostanie tokenu (ADR-M17).
 */
@ConfigurationProperties(prefix = "p360")
public record ServiceClientsProperties(Map<String, Client> serviceClients) {

    public ServiceClientsProperties {
        serviceClients = serviceClients == null ? Map.of() : Map.copyOf(serviceClients);
    }

    public record Client(String secret, List<String> audiences) {

        public Client {
            audiences = audiences == null ? List.of() : List.copyOf(audiences);
        }
    }
}
