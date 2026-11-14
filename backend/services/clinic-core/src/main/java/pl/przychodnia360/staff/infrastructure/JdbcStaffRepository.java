package pl.przychodnia360.staff.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.staff.api.EmployeeType;
import pl.przychodnia360.staff.application.StaffRepository;
import pl.przychodnia360.staff.domain.Employee;
import pl.przychodnia360.staff.domain.ScheduleException;
import pl.przychodnia360.staff.domain.WorkSchedule;

@Repository
class JdbcStaffRepository implements StaffRepository {

    private static final String EMPLOYEE_COLUMNS =
            "id, user_id, type, first_name, last_name, specialization, active, version";

    private final JdbcClient jdbc;

    JdbcStaffRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertEmployee(Employee e) {
        jdbc.sql("""
                INSERT INTO employees (id, tenant_id, user_id, type, first_name, last_name, specialization, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(e.id(), TenantContext.requireTenant().value(), e.userId(), e.type().name(), e.firstName(),
                        e.lastName(), e.specialization(), e.active())
                .update();
    }

    @Override
    public void updateEmployee(Employee e) {
        int rows = jdbc.sql("""
                UPDATE employees SET first_name = ?, last_name = ?, specialization = ?, active = ?,
                       updated_at = now(), version = version + 1
                 WHERE id = ? AND version = ?
                """)
                .params(e.firstName(), e.lastName(), e.specialization(), e.active(), e.id(), e.version()).update();
        if (rows == 0) {
            throw new ConflictException("concurrent-update", "Dane pracownika zostały zmienione równolegle.");
        }
    }

    @Override
    public Optional<Employee> findEmployee(UUID id) {
        return jdbc.sql("SELECT " + EMPLOYEE_COLUMNS + " FROM employees WHERE id = ?").param(id)
                .query(JdbcStaffRepository::employee).optional();
    }

    @Override
    public List<Employee> findEmployees(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + EMPLOYEE_COLUMNS + " FROM employees WHERE id = ANY(?)")
                .param(ids.toArray(UUID[]::new)).query(JdbcStaffRepository::employee).list();
    }

    @Override
    public List<Employee> listEmployees(EmployeeType type, boolean onlyActive) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + EMPLOYEE_COLUMNS + " FROM employees WHERE true");
        if (type != null) {
            sql.append(" AND type = ?");
            params.add(type.name());
        }
        if (onlyActive) {
            sql.append(" AND active");
        }
        sql.append(" ORDER BY lower(last_name), lower(first_name), id");
        return jdbc.sql(sql.toString()).params(params).query(JdbcStaffRepository::employee).list();
    }

    @Override
    public List<WorkSchedule> findWeekly(UUID employeeId) {
        return jdbc.sql("""
                SELECT id, employee_id, day_of_week, start_time, end_time, valid_from, valid_to
                  FROM work_schedules WHERE employee_id = ? ORDER BY day_of_week, start_time
                """)
                .param(employeeId).query(JdbcStaffRepository::weekly).list();
    }

    @Override
    public List<ScheduleException> findExceptions(UUID employeeId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT id, employee_id, date, type, start_time, end_time
                  FROM schedule_exceptions WHERE employee_id = ? AND date BETWEEN ? AND ? ORDER BY date, start_time
                """)
                .params(employeeId, from, to).query(JdbcStaffRepository::exception).list();
    }

    @Override
    public void replaceWeekly(UUID employeeId, List<WorkSchedule> entries) {
        jdbc.sql("DELETE FROM work_schedules WHERE employee_id = ?").param(employeeId).update();
        UUID tenant = TenantContext.requireTenant().value();
        try {
            for (WorkSchedule w : entries) {
                jdbc.sql("""
                        INSERT INTO work_schedules (id, tenant_id, employee_id, day_of_week, start_time, end_time,
                                                    valid_from, valid_to)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                        .params(w.id(), tenant, employeeId, w.dayOfWeek().getValue(), w.startTime(), w.endTime(),
                                w.validFrom(), w.validTo())
                        .update();
            }
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) {
                throw new ConflictException("schedule-overlap", "Dyżury nakładają się w tym samym dniu tygodnia.");
            }
            throw e;
        }
    }

    private static boolean isExclusionViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void insertException(ScheduleException x) {
        jdbc.sql("""
                INSERT INTO schedule_exceptions (id, tenant_id, employee_id, date, type, start_time, end_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .params(x.id(), TenantContext.requireTenant().value(), x.employeeId(), x.date(), x.type().name(),
                        x.startTime(), x.endTime())
                .update();
    }

    @Override
    public boolean deleteException(UUID employeeId, UUID exceptionId) {
        return jdbc.sql("DELETE FROM schedule_exceptions WHERE id = ? AND employee_id = ?")
                .params(exceptionId, employeeId).update() > 0;
    }

    private static Employee employee(ResultSet rs, int row) throws SQLException {
        return new Employee(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                EmployeeType.valueOf(rs.getString("type")), rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("specialization"), rs.getBoolean("active"), rs.getLong("version"));
    }

    private static WorkSchedule weekly(ResultSet rs, int row) throws SQLException {
        return new WorkSchedule(rs.getObject("id", UUID.class), rs.getObject("employee_id", UUID.class),
                DayOfWeek.of(rs.getInt("day_of_week")), rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class), rs.getObject("valid_from", LocalDate.class),
                rs.getObject("valid_to", LocalDate.class));
    }

    private static ScheduleException exception(ResultSet rs, int row) throws SQLException {
        return new ScheduleException(rs.getObject("id", UUID.class), rs.getObject("employee_id", UUID.class),
                rs.getObject("date", LocalDate.class), ScheduleException.Type.valueOf(rs.getString("type")),
                rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class));
    }
}
