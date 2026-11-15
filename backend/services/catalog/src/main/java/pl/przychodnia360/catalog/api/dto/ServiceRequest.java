package pl.przychodnia360.catalog.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import pl.przychodnia360.catalog.api.PerformerType;
import pl.przychodnia360.catalog.application.ServiceCatalogService.ServiceCommand;

public record ServiceRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Min(5) @Max(480) int durationMinutes,
        @NotNull PerformerType performerType) {

    public ServiceCommand toCommand() {
        return new ServiceCommand(categoryId, name, description, price, durationMinutes, performerType);
    }
}
