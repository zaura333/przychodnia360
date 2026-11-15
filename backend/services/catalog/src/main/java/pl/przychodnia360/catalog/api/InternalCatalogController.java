package pl.przychodnia360.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.catalog.api.CatalogFacade.ServiceInfo;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ValidationException;

/**
 * Wewnętrzne API katalogu dla innych usług (zdalna implementacja {@link CatalogFacade}, S1). Nie jest routowane przez
 * krawędź (brak wpisu w tabeli routingu), a placówka wynika z przekazanego tokenu (RLS). W odróżnieniu od API
 * publicznego zwraca też usługi nieaktywne, bo historia wizyt ich potrzebuje.
 */
@RestController
public class InternalCatalogController {

    private static final int MAX_IDS = 200;

    private final CatalogFacade catalog;

    InternalCatalogController(CatalogFacade catalog) {
        this.catalog = catalog;
    }

    @GetMapping(value = "/api/v1/internal/services", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_READ')")
    @Operation(operationId = "internalServicesByIds", summary = "Usługi po identyfikatorach (wewnętrzne, także nieaktywne)",
            description = "Dostęp: SERVICE_READ. Tylko dla innych usług; nieznane identyfikatory są pomijane.")
    public Collection<ServiceInfo> byIds(@RequestParam List<UUID> ids) {
        if (ids.size() > MAX_IDS) {
            throw new ValidationException("Zbyt wiele identyfikatorów (maksymalnie " + MAX_IDS + ").");
        }
        return catalog.services(ids).values();
    }

    @GetMapping(value = "/api/v1/internal/services/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_READ')")
    @Operation(operationId = "internalServiceById", summary = "Usługa po identyfikatorze (wewnętrzne, także nieaktywna)",
            description = "Dostęp: SERVICE_READ. Tylko dla innych usług.")
    public ServiceInfo byId(@PathVariable UUID id) {
        return catalog.service(id).orElseThrow(() -> new NotFoundException("Nie znaleziono usługi."));
    }
}
