package pl.przychodnia360.staff.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.przychodnia360.remote.IdentityFacade;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.staff.api.EmployeeDeactivationGuard;
import pl.przychodnia360.staff.api.EmployeeType;
import pl.przychodnia360.staff.domain.Employee;

@Service
public class EmployeeService {

    /** Dane nowego pracownika; konto powstaje bez hasła (FR-31). */
    public record CreateEmployee(String email, EmployeeType type, String firstName, String lastName,
            String specialization) {
    }

    private final StaffRepository repository;
    private final IdentityFacade identity;
    private final List<EmployeeDeactivationGuard> guards;

    EmployeeService(StaffRepository repository, IdentityFacade identity, List<EmployeeDeactivationGuard> guards) {
        this.repository = repository;
        this.identity = identity;
        this.guards = guards;
    }

    @Transactional
    public Employee create(CreateEmployee command) {
        UUID userId = identity.createEmployeeAccount(command.email(), role(command.type()));
        Employee employee = new Employee(Ids.newId(), userId, command.type(), command.firstName().trim(),
                command.lastName().trim(), blankToNull(command.specialization()), true, 0);
        repository.insertEmployee(employee);
        identity.linkEmployee(userId, employee.id());
        return employee;
    }

    @Transactional
    public Employee update(UUID id, String firstName, String lastName, String specialization) {
        Employee updated = get(id).withDetails(firstName.trim(), lastName.trim(), blankToNull(specialization));
        repository.updateEmployee(updated);
        return get(id);
    }

    /** Dezaktywacja blokowana przez przyszłe wizyty: 409 z ich liczbą. Konto użytkownika też jest dezaktywowane. */
    @Transactional
    public void deactivate(UUID id) {
        Employee employee = get(id);
        if (!employee.active()) {
            return;
        }
        long blocking = guards.stream().mapToLong(g -> g.blockingFutureWork(id)).sum();
        if (blocking > 0) {
            throw new ConflictException("employee-has-future-appointments",
                    "Pracownik ma przyszłe wizyty (" + blocking + "). Najpierw je odwołaj lub przenieś.");
        }
        repository.updateEmployee(employee.deactivated());
        identity.deactivate(employee.userId());
    }

    @Transactional(readOnly = true)
    public Employee get(UUID id) {
        return repository.findEmployee(id).orElseThrow(() -> new NotFoundException("Nie znaleziono pracownika."));
    }

    @Transactional(readOnly = true)
    public List<Employee> list(EmployeeType type, boolean onlyActive) {
        return repository.listEmployees(type, onlyActive);
    }

    private static Role role(EmployeeType type) {
        return switch (type) {
            case DOCTOR -> Role.DOCTOR;
            case MEDICAL_STAFF -> Role.MEDICAL_STAFF;
            case RECEPTIONIST -> Role.RECEPTIONIST;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
