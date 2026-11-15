package pl.przychodnia360.tenant.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.security.PublicEndpoint;
import pl.przychodnia360.tenant.api.dto.FacilityResponse;
import pl.przychodnia360.tenant.application.FacilityQueryService;

@RestController
public class FacilityController {

    private final FacilityQueryService service;

    FacilityController(FacilityQueryService service) {
        this.service = service;
    }

    @PublicEndpoint
    @GetMapping(value = "/api/v1/facilities", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listFacilities", summary = "Lista aktywnych placówek", description = "Dostęp: publiczny (wybór placówki przy logowaniu).")
    public List<FacilityResponse> list() {
        return service.listActive().stream().map(f -> new FacilityResponse(f.slug(), f.name())).toList();
    }
}
