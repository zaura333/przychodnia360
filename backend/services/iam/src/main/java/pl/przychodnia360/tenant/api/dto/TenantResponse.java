package pl.przychodnia360.tenant.api.dto;

import java.util.UUID;
import pl.przychodnia360.tenant.api.TenantFacade.TenantInfo;

public record TenantResponse(UUID id, String slug, String name, boolean active) {

    public static TenantResponse of(TenantInfo t) {
        return new TenantResponse(t.id(), t.slug(), t.name(), t.active());
    }
}
