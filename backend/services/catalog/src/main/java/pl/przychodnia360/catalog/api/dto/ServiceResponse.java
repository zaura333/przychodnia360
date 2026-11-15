package pl.przychodnia360.catalog.api.dto;

import java.math.BigDecimal;
import java.util.UUID;
import pl.przychodnia360.catalog.api.PerformerType;
import pl.przychodnia360.catalog.domain.MedicalService;

public record ServiceResponse(UUID id, UUID categoryId, String name, String description, BigDecimal price,
        int durationMinutes, PerformerType performerType, boolean active) {

    public static ServiceResponse of(MedicalService s) {
        return new ServiceResponse(s.id(), s.categoryId(), s.name(), s.description(), s.price(),
                s.durationMinutes(), s.performerType(), s.active());
    }
}
