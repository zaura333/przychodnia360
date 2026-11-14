package pl.przychodnia360.appointments.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.przychodnia360.staff.api.EmployeeDeactivationGuard;

/** Dezaktywacja pracownika z przyszłymi wizytami jest blokowana (409 z liczbą wizyt). */
@Component
class AppointmentDeactivationGuard implements EmployeeDeactivationGuard {

    private final AppointmentRepository repository;
    private final Clock clock;

    AppointmentDeactivationGuard(AppointmentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public long blockingFutureWork(UUID employeeId) {
        return repository.countScheduledAfter(employeeId, clock.instant());
    }
}
