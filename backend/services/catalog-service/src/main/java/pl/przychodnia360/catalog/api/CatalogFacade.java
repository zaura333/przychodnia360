package pl.przychodnia360.catalog.api;

import pl.przychodnia360.shared.PerformerType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Publiczny interfejs modułu katalogu dla innych modułów (rezerwacje). */
public interface CatalogFacade {

    /** Dane usługi potrzebne do rezerwacji. */
    record ServiceInfo(UUID id, String name, BigDecimal price, int durationMinutes, PerformerType performerType,
            boolean active) {
    }

    Optional<ServiceInfo> service(UUID serviceId);

    /** Usługi po identyfikatorach, także nieaktywne (historia wizyt); nieznane są pomijane. */
    Map<UUID, ServiceInfo> services(Collection<UUID> ids);
}
