package pl.przychodnia360.staff.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WeeklyScheduleRequest(@NotNull @Size(max = 50) List<@Valid WeeklyEntryRequest> entries) {
}
