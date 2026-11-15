package pl.przychodnia360.catalog.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import pl.przychodnia360.catalog.api.CatalogFacade;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ServiceUnavailableException;

/**
 * Zdalna implementacja {@link CatalogFacade} (S1, ADR-M14): zapytania REST do catalog-service. Włączana właściwością
 * {@code p360.catalog.mode=remote} (domyślnie lokalna implementacja w tym procesie). Rezerwacja (jedna usługa) przy awarii
 * dostaje 503; widoki wizyt (wiele usług) degradują się do pustych nazw usług, bez blokowania przeglądania wizyt.
 */
@Component
@Primary
@ConditionalOnProperty(name = "p360.catalog.mode", havingValue = "remote")
class RemoteCatalogFacade implements CatalogFacade {

    private static final Logger log = LoggerFactory.getLogger(RemoteCatalogFacade.class);
    private static final ParameterizedTypeReference<ServiceInfo> ONE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<ServiceInfo>> MANY = new ParameterizedTypeReference<>() { };

    private final RemoteServiceClient client;

    RemoteCatalogFacade(@org.springframework.beans.factory.annotation.Qualifier("catalogClient") RemoteServiceClient client) {
        this.client = client;
    }

    @Override
    public Optional<ServiceInfo> service(UUID serviceId) {
        try {
            return Optional.ofNullable(client.get("/api/v1/internal/services/{id}", ONE, serviceId));
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Map<UUID, ServiceInfo> services(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String csv = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        try {
            List<ServiceInfo> services = client.get("/api/v1/internal/services?ids={ids}", MANY, csv);
            return services.stream().collect(Collectors.toMap(ServiceInfo::id, s -> s, (a, b) -> a));
        } catch (ServiceUnavailableException e) {
            log.warn("Katalog niedostępny, widok bez nazw usług: {}", e.getMessage());
            return Map.of();
        }
    }
}
