package pl.przychodnia360.staff.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.staff.api.StaffDtos.EmployeeResponse;
import pl.przychodnia360.staff.api.StaffDtos.ExceptionResponse;
import pl.przychodnia360.staff.api.StaffDtos.ScheduleResponse;
import pl.przychodnia360.staff.api.StaffDtos.WeeklyEntryResponse;
import pl.przychodnia360.staff.application.EmployeeService;
import pl.przychodnia360.staff.application.EmployeeService.CreateEmployee;
import pl.przychodnia360.staff.application.ScheduleService;
import pl.przychodnia360.staff.application.ScheduleService.WeeklyEntry;
import pl.przychodnia360.staff.domain.ScheduleException;

/** Zarządzanie pracownikami i ich grafikami przez administratora placówki (FR-31, FR-34). */
@RestController
@org.springframework.web.bind.annotation.RequestMapping(value = "/api/v1/admin/employees",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class EmployeeAdminController {

    public record CreateEmployeeRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull EmployeeType type,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 200) String specialization) {
    }

    public record UpdateEmployeeRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 200) String specialization) {
    }

    public record WeeklyEntryRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime,
            @NotNull LocalTime endTime, @NotNull LocalDate validFrom, LocalDate validTo) {
    }

    public record WeeklyScheduleRequest(@NotNull @Size(max = 50) List<@Valid WeeklyEntryRequest> entries) {
    }

    public record ExceptionRequest(@NotNull LocalDate date, @NotNull ScheduleException.Type type, LocalTime startTime,
            LocalTime endTime) {
    }

    private final EmployeeService employees;
    private final ScheduleService schedules;

    EmployeeAdminController(EmployeeService employees, ScheduleService schedules) {
        this.employees = employees;
        this.schedules = schedules;
    }

    @GetMapping
    @PreAuthorize("@permissions.has(authentication, 'EMPLOYEE_MANAGE')")
    @Operation(operationId = "listEmployees", summary = "Lista pracowników", description = "Dostęp: EMPLOYEE_MANAGE (TENANT_ADMIN).")
    public List<EmployeeResponse> list(@RequestParam(required = false) EmployeeType type,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        return employees.list(type, onlyActive).stream().map(EmployeeResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has(authentication, 'EMPLOYEE_MANAGE')")
    @Operation(operationId = "createEmployee", summary = "Dodanie pracownika z kontem",
            description = "Dostęp: EMPLOYEE_MANAGE. Konto powstaje bez hasła; pracownik ustawia je z linku w e-mailu.")
    public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest request) {
        return EmployeeResponse.of(employees.create(new CreateEmployee(request.email(), request.type(),
                request.firstName(), request.lastName(), request.specialization())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissions.has(authentication, 'EMPLOYEE_MANAGE')")
    @Operation(operationId = "updateEmployee", summary = "Edycja pracownika", description = "Dostęp: EMPLOYEE_MANAGE.")
    public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return EmployeeResponse.of(employees.update(id, request.firstName(), request.lastName(),
                request.specialization()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissions.has(authentication, 'EMPLOYEE_MANAGE')")
    @Operation(operationId = "deactivateEmployee", summary = "Dezaktywacja pracownika",
            description = "Dostęp: EMPLOYEE_MANAGE. 409, gdy pracownik ma przyszłe wizyty.")
    public void deactivate(@PathVariable UUID id) {
        employees.deactivate(id);
    }

    @GetMapping("/{id}/schedule")
    @PreAuthorize("@permissions.has(authentication, 'SCHEDULE_MANAGE')")
    @Operation(operationId = "getEmployeeSchedule", summary = "Grafik pracownika w oknie", description = "Dostęp: SCHEDULE_MANAGE.")
    public ScheduleResponse schedule(@PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ScheduleResponse.of(schedules.view(id, from, to));
    }

    @PutMapping("/{id}/schedule")
    @PreAuthorize("@permissions.has(authentication, 'SCHEDULE_MANAGE')")
    @Operation(operationId = "replaceEmployeeSchedule", summary = "Zastąpienie grafiku tygodniowego",
            description = "Dostęp: SCHEDULE_MANAGE. 409 przy nakładających się dyżurach.")
    public List<WeeklyEntryResponse> replaceSchedule(@PathVariable UUID id,
            @Valid @RequestBody WeeklyScheduleRequest request) {
        List<WeeklyEntry> entries = request.entries().stream().map(e -> new WeeklyEntry(e.dayOfWeek(), e.startTime(),
                e.endTime(), e.validFrom(), e.validTo())).toList();
        return schedules.defineWeekly(id, entries).stream().map(WeeklyEntryResponse::of).toList();
    }

    @PostMapping("/{id}/schedule-exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has(authentication, 'SCHEDULE_MANAGE')")
    @Operation(operationId = "addScheduleException", summary = "Urlop lub dodatkowa zmiana", description = "Dostęp: SCHEDULE_MANAGE.")
    public ExceptionResponse addException(@PathVariable UUID id, @Valid @RequestBody ExceptionRequest request) {
        return ExceptionResponse.of(schedules.addException(id, request.date(), request.type(), request.startTime(),
                request.endTime()));
    }

    @DeleteMapping("/{id}/schedule-exceptions/{exceptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissions.has(authentication, 'SCHEDULE_MANAGE')")
    @Operation(operationId = "deleteScheduleException", summary = "Usunięcie wyjątku grafiku", description = "Dostęp: SCHEDULE_MANAGE.")
    public void deleteException(@PathVariable UUID id, @PathVariable UUID exceptionId) {
        schedules.deleteException(id, exceptionId);
    }
}
