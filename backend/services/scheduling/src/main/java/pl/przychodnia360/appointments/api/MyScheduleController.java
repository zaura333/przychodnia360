package pl.przychodnia360.appointments.api;

import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.appointments.api.dto.IntervalResponse;
import pl.przychodnia360.appointments.api.dto.MyScheduleResponse;
import pl.przychodnia360.appointments.application.AppointmentQueryService;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.staff.api.StaffFacade;

/** Własny grafik i wizyty lekarza lub personelu medycznego (FR-14, FR-53). Leży w appointments, bo łączy grafik z wizytami. */
@RestController
public class MyScheduleController {

    private final StaffFacade staff;
    private final AppointmentQueryService queries;

    MyScheduleController(StaffFacade staff, AppointmentQueryService queries) {
        this.staff = staff;
        this.queries = queries;
    }

    @GetMapping(value = "/api/v1/me/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'SCHEDULE_READ_OWN')")
    @Operation(operationId = "getMySchedule", summary = "Własny grafik i wizyty",
            description = "Dostęp: SCHEDULE_READ_OWN (DOCTOR, MEDICAL_STAFF). Okno do 32 dni.")
    public MyScheduleResponse mine(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (user.employeeId() == null) {
            throw new NotFoundException("Konto nie jest powiązane z pracownikiem.");
        }
        List<IntervalResponse> intervals = staff.workingIntervals(user.employeeId(), from, to).stream()
                .map(i -> new IntervalResponse(i.start(), i.end())).toList();
        return new MyScheduleResponse(intervals, queries.forEmployee(user.employeeId(), from, to));
    }
}
