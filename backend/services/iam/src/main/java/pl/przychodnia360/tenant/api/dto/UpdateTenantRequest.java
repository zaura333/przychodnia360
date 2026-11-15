package pl.przychodnia360.tenant.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(@Size(min = 1, max = 200) String name, Boolean active) {
}
