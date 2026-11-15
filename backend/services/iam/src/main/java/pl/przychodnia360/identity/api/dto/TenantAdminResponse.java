package pl.przychodnia360.identity.api.dto;

import java.util.UUID;

public record TenantAdminResponse(UUID userId, String email) {
}
