package pl.przychodnia360;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pl.przychodnia360.catalog.api.CatalogFacade;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;

/**
 * Atrapa {@link CatalogFacade} w testach scheduling (moduł katalogu jest w catalog-service): usługi w pamięci, rozdzielone
 * placówkami jak przy zdalnym odczycie z RLS. Zarządzanie katalogiem jest sprawdzane w testach catalog-service.
 */
public class FakeCatalog implements CatalogFacade {

    private final Map<UUID, Map<UUID, ServiceInfo>> byTenant = new ConcurrentHashMap<>();

    public UUID add(UUID tenant, String name, BigDecimal price, int durationMinutes, String performerType) {
        UUID id = Ids.newId();
        byTenant.computeIfAbsent(tenant, t -> new ConcurrentHashMap<>())
                .put(id, new ServiceInfo(id, name, price, durationMinutes, performerType, true));
        return id;
    }

    /** Usunięcie logiczne: usługa nadal jest zwracana (historia wizyt), ale jako nieaktywna. */
    public void deactivate(UUID tenant, UUID id) {
        byTenant.get(tenant).computeIfPresent(id, (k, s) -> new ServiceInfo(s.id(), s.name(), s.price(),
                s.durationMinutes(), s.performerType(), false));
    }

    public List<ServiceInfo> servicesOf(UUID tenant) {
        return new ArrayList<>(byTenant.getOrDefault(tenant, Map.of()).values());
    }

    private Map<UUID, ServiceInfo> current() {
        return byTenant.getOrDefault(TenantContext.requireTenant().value(), Map.of());
    }

    @Override
    public Optional<ServiceInfo> service(UUID serviceId) {
        return Optional.ofNullable(current().get(serviceId));
    }

    @Override
    public Map<UUID, ServiceInfo> services(Collection<UUID> ids) {
        Map<UUID, ServiceInfo> result = new java.util.HashMap<>();
        Map<UUID, ServiceInfo> all = current();
        ids.forEach(id -> {
            if (all.containsKey(id)) {
                result.put(id, all.get(id));
            }
        });
        return result;
    }
}
