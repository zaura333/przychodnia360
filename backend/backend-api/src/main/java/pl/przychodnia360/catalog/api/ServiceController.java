package pl.przychodnia360.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.catalog.application.CatalogRepository.ServiceFilter;
import pl.przychodnia360.catalog.application.ServiceCatalogService;
import pl.przychodnia360.catalog.domain.MedicalService;
import pl.przychodnia360.common.web.PageResponse;

/** Odczyt katalogu: usługi (filtry, FR-02, FR-22) i kategorie. */
@RestController
public class ServiceController {

    public record ServiceResponse(UUID id, UUID categoryId, String name, String description, BigDecimal price,
            int durationMinutes, PerformerType performerType, boolean active) {

        static ServiceResponse of(MedicalService s) {
            return new ServiceResponse(s.id(), s.categoryId(), s.name(), s.description(), s.price(),
                    s.durationMinutes(), s.performerType(), s.active());
        }
    }

    public record CategoryResponse(UUID id, String name) {
    }

    private final ServiceCatalogService service;

    ServiceController(ServiceCatalogService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/v1/services", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_READ')")
    @Operation(operationId = "listServices", summary = "Lista usług z filtrami",
            description = "Dostęp: SERVICE_READ (wszystkie role placówki). Tylko usługi aktywne.")
    public PageResponse<ServiceResponse> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) PerformerType performerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(new ServiceFilter(categoryId, minPrice, maxPrice, performerType), page, size)
                .map(ServiceResponse::of);
    }

    @GetMapping(value = "/api/v1/services/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_READ')")
    @Operation(operationId = "getService", summary = "Szczegóły usługi",
            description = "Dostęp: SERVICE_READ. Cena i czas trwania (FR-22).")
    public ServiceResponse get(@PathVariable UUID id) {
        return ServiceResponse.of(service.get(id));
    }

    @GetMapping(value = "/api/v1/service-categories", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_READ')")
    @Operation(operationId = "listServiceCategories", summary = "Kategorie usług", description = "Dostęp: SERVICE_READ.")
    public List<CategoryResponse> categories() {
        return service.categories().stream().map(c -> new CategoryResponse(c.id(), c.name())).toList();
    }
}
