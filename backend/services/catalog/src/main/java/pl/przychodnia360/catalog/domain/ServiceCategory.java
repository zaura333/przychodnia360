package pl.przychodnia360.catalog.domain;

import java.util.UUID;

public record ServiceCategory(UUID id, String name, long version) {
}
