package pl.przychodnia360.shared;

import java.util.Objects;
import java.util.UUID;

/** Identyfikator placówki (tenanta). Typ wartościowy, żeby nie mylić go z innymi UUID. */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "value");
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
