package pl.przychodnia360.remote;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import pl.przychodnia360.shared.PerformerType;

/** Dane katalogu potrzebne rezerwacjom; źródłem jest catalog-service (wewnętrzne API, ADR-M07). */
public interface CatalogFacade {

    /** Dane usługi potrzebne do rezerwacji. */
    record ServiceInfo(UUID id, String name, BigDecimal price, int durationMinutes, PerformerType performerType,
            boolean active) {
    }

    Optional<ServiceInfo> service(UUID serviceId);

    /** Usługi po identyfikatorach, także nieaktywne (historia wizyt); nieznane są pomijane. */
    Map<UUID, ServiceInfo> services(Collection<UUID> ids);
}
