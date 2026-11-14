package pl.przychodnia360.catalog.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Publiczny interfejs modułu katalogu dla innych modułów (rezerwacje). */
public interface CatalogFacade {

    /** Dane usługi potrzebne do rezerwacji. */
    record ServiceInfo(UUID id, String name, BigDecimal price, int durationMinutes, PerformerType performerType,
            boolean active) {
    }

    Optional<ServiceInfo> service(UUID serviceId);
}
