package pl.przychodnia360.tenant.api;

import java.util.Optional;
import java.util.UUID;

/** Publiczny interfejs modułu tenant dla innych modułów. */
public interface TenantFacade {

    /** Identyfikator aktywnej placówki o danym slugu (funkcja resolve_tenant). */
    Optional<UUID> resolveActiveSlug(String slug);

    Optional<String> name(UUID tenantId);
}
