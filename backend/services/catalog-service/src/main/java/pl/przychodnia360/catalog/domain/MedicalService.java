package pl.przychodnia360.catalog.domain;

import java.math.BigDecimal;
import java.util.UUID;
import pl.przychodnia360.shared.PerformerType;

/** Usługa medyczna placówki. Usunięcie jest logiczne (active = false), wizyty historyczne zachowują powiązanie. */
public record MedicalService(UUID id, UUID categoryId, String name, String description, BigDecimal price,
        int durationMinutes, PerformerType performerType, boolean active, long version) {

    public MedicalService deactivated() {
        return new MedicalService(id, categoryId, name, description, price, durationMinutes, performerType, false,
                version);
    }
}
