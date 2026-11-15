package pl.przychodnia360.catalog.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.catalog.api.CatalogFacade.ServiceInfo;
import pl.przychodnia360.common.internal.InternalApi;

/** Wewnętrzne API katalogu dla scheduling-service (ADR-M07); poza kontraktem publicznym i krawędzią. */
@Hidden
@RestController
public class InternalCatalogController {

    public record IdsRequest(@NotNull @Size(max = 500) List<@NotNull UUID> ids) {
    }

    private final CatalogFacade catalog;

    InternalCatalogController(CatalogFacade catalog) {
        this.catalog = catalog;
    }

    /** Usługi po identyfikatorach, także nieaktywne (historia wizyt); nieznane są pomijane. */
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_READ')")
    @PostMapping(value = InternalApi.PREFIX + "/services/lookup", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ServiceInfo> lookup(@Valid @RequestBody IdsRequest request) {
        return List.copyOf(catalog.services(request.ids()).values());
    }
}
