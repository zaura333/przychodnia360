package pl.przychodnia360.staff.api.dto;

import java.util.List;
import java.util.UUID;

public record DoctorResponse(UUID id, String firstName, String lastName, String specialization,
        List<WeeklyEntryResponse> weekly) {
}
