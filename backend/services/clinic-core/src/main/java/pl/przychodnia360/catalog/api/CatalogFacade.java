package pl.przychodnia360.catalog.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Publiczny interfejs modułu katalogu dla innych modułów (rezerwacje). */
public interface CatalogFacade {

    /**
     * Dane usługi potrzebne do rezerwacji. {@code performerType} to wartość kontraktu ({@code DOCTOR} lub
     * {@code MEDICAL_STAFF}), nie klasa modułu, bo konsument może być w innej usłudze.
     */
    record ServiceInfo(UUID id, String name, BigDecimal price, int durationMinutes, String performerType,
            boolean active) {
    }

    Optional<ServiceInfo> service(UUID serviceId);

    /** Usługi po identyfikatorach, także nieaktywne (historia wizyt); nieznane są pomijane. */
    Map<UUID, ServiceInfo> services(Collection<UUID> ids);
}
