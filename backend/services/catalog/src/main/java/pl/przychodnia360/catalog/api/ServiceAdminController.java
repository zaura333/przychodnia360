package pl.przychodnia360.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.catalog.api.dto.CategoryRequest;
import pl.przychodnia360.catalog.api.dto.CategoryResponse;
import pl.przychodnia360.catalog.api.dto.ServiceRequest;
import pl.przychodnia360.catalog.api.dto.ServiceResponse;
import pl.przychodnia360.catalog.application.ServiceCatalogService;

/** Zarządzanie katalogiem przez administratora placówki (FR-33, FR-57). */
@RestController
public class ServiceAdminController {

    private final ServiceCatalogService service;

    ServiceAdminController(ServiceCatalogService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/v1/admin/services", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_MANAGE')")
    @Operation(operationId = "createService", summary = "Dodanie usługi", description = "Dostęp: SERVICE_MANAGE (TENANT_ADMIN).")
    public ServiceResponse create(@Valid @RequestBody ServiceRequest request) {
        return ServiceResponse.of(service.create(request.toCommand()));
    }

    @PutMapping(value = "/api/v1/admin/services/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_MANAGE')")
    @Operation(operationId = "updateService", summary = "Edycja usługi", description = "Dostęp: SERVICE_MANAGE (TENANT_ADMIN).")
    public ServiceResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceRequest request) {
        return ServiceResponse.of(service.update(id, request.toCommand()));
    }

    @DeleteMapping("/api/v1/admin/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_MANAGE')")
    @Operation(operationId = "deactivateService", summary = "Dezaktywacja usługi (usunięcie logiczne)",
            description = "Dostęp: SERVICE_MANAGE. Historia wizyt zostaje.")
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    @PostMapping(value = "/api/v1/admin/service-categories", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_MANAGE')")
    @Operation(operationId = "createServiceCategory", summary = "Dodanie kategorii", description = "Dostęp: SERVICE_MANAGE.")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        var c = service.createCategory(request.name());
        return new CategoryResponse(c.id(), c.name());
    }

    @PutMapping(value = "/api/v1/admin/service-categories/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_MANAGE')")
    @Operation(operationId = "renameServiceCategory", summary = "Zmiana nazwy kategorii", description = "Dostęp: SERVICE_MANAGE.")
    public CategoryResponse renameCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        var c = service.renameCategory(id, request.name());
        return new CategoryResponse(c.id(), c.name());
    }

    @DeleteMapping("/api/v1/admin/service-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissions.has(authentication, 'SERVICE_MANAGE')")
    @Operation(operationId = "deleteServiceCategory", summary = "Usunięcie kategorii",
            description = "Dostęp: SERVICE_MANAGE. 409, gdy kategoria ma aktywne usługi.")
    public void deleteCategory(@PathVariable UUID id) {
        service.deleteCategory(id);
    }
}
