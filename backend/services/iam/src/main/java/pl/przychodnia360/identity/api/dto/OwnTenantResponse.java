package pl.przychodnia360.identity.api.dto;

import java.util.UUID;

/** Dane własnej placówki (FR-43, odczyt dla każdej roli placówki). */
public record OwnTenantResponse(UUID id, String slug, String name) {
}
