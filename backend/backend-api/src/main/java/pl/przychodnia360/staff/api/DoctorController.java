package pl.przychodnia360.staff.api;

import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.staff.api.StaffDtos.ScheduleResponse;
import pl.przychodnia360.staff.api.StaffDtos.WeeklyEntryResponse;
import pl.przychodnia360.staff.application.EmployeeService;
import pl.przychodnia360.staff.application.ScheduleService;
import pl.przychodnia360.staff.domain.Employee;

/** Lista lekarzy i ich grafiki dla pacjentów i rejestratorów (FR-39). */
@RestController
public class DoctorController {

    public record DoctorResponse(UUID id, String firstName, String lastName, String specialization,
            List<WeeklyEntryResponse> weekly) {
    }

    private final EmployeeService employees;
    private final ScheduleService schedules;

    DoctorController(EmployeeService employees, ScheduleService schedules) {
        this.employees = employees;
        this.schedules = schedules;
    }

    @GetMapping(value = "/api/v1/doctors", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SCHEDULE_READ')")
    @Operation(operationId = "listDoctors", summary = "Lista lekarzy z podstawowym grafikiem",
            description = "Dostęp: SCHEDULE_READ (wszystkie role placówki). Tylko aktywni lekarze.")
    public List<DoctorResponse> doctors() {
        return employees.list(EmployeeType.DOCTOR, true).stream()
                .map(d -> new DoctorResponse(d.id(), d.firstName(), d.lastName(), d.specialization(),
                        schedules.weekly(d.id()).stream().map(WeeklyEntryResponse::of).toList()))
                .toList();
    }

    @GetMapping(value = "/api/v1/doctors/{id}/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SCHEDULE_READ')")
    @Operation(operationId = "getDoctorSchedule", summary = "Grafik lekarza i przedziały pracy w oknie",
            description = "Dostęp: SCHEDULE_READ. Okno maksymalnie 31 dni; daty lokalne placówki (Europe/Warsaw).")
    public ScheduleResponse schedule(@PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Employee doctor = employees.get(id);
        if (doctor.type() != EmployeeType.DOCTOR) {
            throw new NotFoundException("Nie znaleziono lekarza.");
        }
        return ScheduleResponse.of(schedules.view(id, from, to));
    }
}
