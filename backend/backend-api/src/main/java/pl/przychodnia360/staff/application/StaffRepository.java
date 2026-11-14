package pl.przychodnia360.staff.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pl.przychodnia360.staff.api.EmployeeType;
import pl.przychodnia360.staff.domain.Employee;
import pl.przychodnia360.staff.domain.ScheduleException;
import pl.przychodnia360.staff.domain.WorkSchedule;

public interface StaffRepository {

    void insertEmployee(Employee employee);

    void updateEmployee(Employee employee);

    Optional<Employee> findEmployee(UUID id);

    List<Employee> findEmployees(Collection<UUID> ids);

    /** Pracownicy (aktywni lub wszyscy) wybranego typu; type null = wszystkie typy. */
    List<Employee> listEmployees(EmployeeType type, boolean onlyActive);

    List<WorkSchedule> findWeekly(UUID employeeId);

    List<ScheduleException> findExceptions(UUID employeeId, LocalDate from, LocalDate to);

    /** Zastępuje grafik tygodniowy; nakładające się dyżury to ConflictException (schedule-overlap). */
    void replaceWeekly(UUID employeeId, List<WorkSchedule> entries);

    void insertException(ScheduleException exception);

    boolean deleteException(UUID employeeId, UUID exceptionId);
}
