package pl.przychodnia360.remote;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import pl.przychodnia360.common.internal.InternalClients;
import pl.przychodnia360.common.security.Role;

/**
 * Implementacje fasad zdalnych przez wewnętrzne API (ADR-M07): synchroniczny REST z przekazaniem tokenu użytkownika.
 * Świadome uproszczenie pierwszej wersji; docelowo dane referencyjne z lokalnych kopii zasilanych zdarzeniami.
 */
@Configuration
class HttpRemoteClients {

    record IdsRequest(Collection<UUID> ids) {
    }

    record AccountRequest(String email, Role role) {
    }

    record AccountResponse(UUID userId) {
    }

    record LinkRequest(UUID employeeId) {
    }

    @Bean
    CatalogFacade catalogFacade(@Value("${p360.services.catalog-url}") String url,
            @Value("${p360.internal.token:}") String token) {
        RestClient client = InternalClients.create(url, token);
        return new CatalogFacade() {
            @Override
            public Optional<ServiceInfo> service(UUID serviceId) {
                return Optional.ofNullable(services(List.of(serviceId)).get(serviceId));
            }

            @Override
            public Map<UUID, ServiceInfo> services(Collection<UUID> ids) {
                if (ids.isEmpty()) {
                    return Map.of();
                }
                List<ServiceInfo> found = client.post().uri("/services/lookup").body(new IdsRequest(ids))
                        .retrieve().body(new ParameterizedTypeReference<List<ServiceInfo>>() { });
                return byId(found, ServiceInfo::id);
            }
        };
    }

    @Bean
    PatientFacade patientFacade(@Value("${p360.services.iam-url}") String url,
            @Value("${p360.internal.token:}") String token) {
        RestClient client = InternalClients.create(url, token);
        return new PatientFacade() {
            @Override
            public Optional<PatientInfo> find(UUID patientId) {
                return Optional.ofNullable(findAll(List.of(patientId)).get(patientId));
            }

            @Override
            public Map<UUID, PatientInfo> findAll(Collection<UUID> ids) {
                if (ids.isEmpty()) {
                    return Map.of();
                }
                List<PatientInfo> found = client.post().uri("/patients/lookup").body(new IdsRequest(ids))
                        .retrieve().body(new ParameterizedTypeReference<List<PatientInfo>>() { });
                return byId(found, PatientInfo::id);
            }
        };
    }

    @Bean
    IdentityFacade identityFacade(@Value("${p360.services.iam-url}") String url,
            @Value("${p360.internal.token:}") String token) {
        RestClient client = InternalClients.create(url, token);
        return new IdentityFacade() {
            @Override
            public UUID createEmployeeAccount(String email, Role role) {
                return client.post().uri("/employee-accounts").body(new AccountRequest(email, role))
                        .retrieve().body(AccountResponse.class).userId();
            }

            @Override
            public void linkEmployee(UUID userId, UUID employeeId) {
                client.post().uri("/users/{id}/employee-link", userId).body(new LinkRequest(employeeId))
                        .retrieve().toBodilessEntity();
            }

            @Override
            public void deactivate(UUID userId) {
                client.post().uri("/users/{id}/deactivate", userId).retrieve().toBodilessEntity();
            }
        };
    }

    private static <T> Map<UUID, T> byId(List<T> items, Function<T, UUID> id) {
        return items == null ? Map.of() : items.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }
}
