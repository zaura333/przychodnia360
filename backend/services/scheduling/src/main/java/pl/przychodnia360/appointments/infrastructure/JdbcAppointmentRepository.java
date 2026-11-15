package pl.przychodnia360.appointments.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.appointments.application.AppointmentRepository;
import pl.przychodnia360.appointments.domain.Appointment;
import pl.przychodnia360.appointments.domain.AppointmentStatus;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.staff.api.TimeSlot;

@Repository
class JdbcAppointmentRepository implements AppointmentRepository {

    private static final String COLUMNS = """
            id, patient_id, employee_id, service_id, lower(time_range) AS start_at, upper(time_range) AS end_at,
            status, booked_by_user_id, cancelled_by_user_id, cancelled_at, version""";

    private final JdbcClient jdbc;

    JdbcAppointmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Appointment a) {
        try {
            jdbc.sql("""
                    INSERT INTO appointments (id, tenant_id, patient_id, employee_id, service_id, time_range, status,
                                              booked_by_user_id)
                    VALUES (?, ?, ?, ?, ?, tstzrange(?, ?, '[)'), ?, ?)
                    """)
                    .params(a.id(), TenantContext.requireTenant().value(), a.patientId(), a.employeeId(), a.serviceId(),
                            utc(a.startAt()), utc(a.endAt()), a.status().name(), a.bookedByUserId())
                    .update();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        } catch (PessimisticLockingFailureException e) {
            throw lockConflict();
        }
    }

    @Override
    public void update(Appointment a) {
        try {
            int rows = jdbc.sql("""
                    UPDATE appointments SET time_range = tstzrange(?, ?, '[)'), status = ?, cancelled_by_user_id = ?,
                           cancelled_at = ?, updated_at = now(), version = version + 1
                     WHERE id = ? AND version = ?
                    """)
                    .params(utc(a.startAt()), utc(a.endAt()), a.status().name(), a.cancelledByUserId(),
                            a.cancelledAt() == null ? null : utc(a.cancelledAt()), a.id(), a.version())
                    .update();
            if (rows == 0) {
                throw new ConflictException("concurrent-update",
                        "Wizyta została zmieniona równolegle. Odśwież i spróbuj ponownie.");
            }
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        } catch (PessimisticLockingFailureException e) {
            throw lockConflict();
        }
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM appointments WHERE id = ?").param(id)
                .query(JdbcAppointmentRepository::map).optional();
    }

    @Override
    public List<Appointment> search(UUID employeeId, UUID patientId, List<AppointmentStatus> statuses, Instant from,
            Instant to) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM appointments WHERE true");
        if (employeeId != null) {
            sql.append(" AND employee_id = ?");
            params.add(employeeId);
        }
        if (patientId != null) {
            sql.append(" AND patient_id = ?");
            params.add(patientId);
        }
        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" AND status = ANY(?)");
            params.add(statuses.stream().map(Enum::name).toArray(String[]::new));
        }
        if (from != null) {
            sql.append(" AND lower(time_range) >= ?");
            params.add(utc(from));
        }
        if (to != null) {
            sql.append(" AND lower(time_range) < ?");
            params.add(utc(to));
        }
        sql.append(" ORDER BY lower(time_range), id");
        return jdbc.sql(sql.toString()).params(params).query(JdbcAppointmentRepository::map).list();
    }

    @Override
    public List<TimeSlot> busyIntervals(UUID employeeId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT lower(time_range) AS start_at, upper(time_range) AS end_at FROM appointments
                 WHERE employee_id = ? AND status IN ('SCHEDULED', 'COMPLETED')
                   AND time_range && tstzrange(?, ?, '[)')
                 ORDER BY lower(time_range)
                """)
                .params(employeeId, utc(from), utc(to))
                .query((rs, i) -> new TimeSlot(instant(rs, "start_at"), instant(rs, "end_at"))).list();
    }

    @Override
    public long countScheduledAfter(UUID employeeId, Instant now) {
        return jdbc.sql("SELECT count(*) FROM appointments WHERE employee_id = ? AND status = 'SCHEDULED' "
                + "AND lower(time_range) > ?").params(employeeId, utc(now)).query(Long.class).single();
    }

    /** Naruszenie ograniczenia wykluczającego (SQLSTATE 23P01) to kolizja terminu; rodzaj po nazwie ograniczenia. */
    /**
     * Równoczesne rezerwacje tego samego terminu: PostgreSQL może przerwać jedną z nich zakleszczeniem (40P01) przy
     * sprawdzaniu ograniczenia wykluczającego zamiast naruszenia ograniczenia; dla użytkownika to ten sam wynik
     * (termin zajęty), a nie błąd serwera.
     */
    private static ConflictException lockConflict() {
        return new ConflictException("slot-taken", "Ten termin jest już zajęty.");
    }

    private static RuntimeException translate(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                String message = sql.getMessage() == null ? "" : sql.getMessage();
                if (message.contains("ex_appointments_patient")) {
                    return new ConflictException("patient-overlap", "Pacjent ma już wizytę w tym czasie.");
                }
                return new ConflictException("slot-taken", "Ten termin jest już zajęty.");
            }
        }
        return e;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Appointment map(ResultSet rs, int row) throws SQLException {
        OffsetDateTime cancelledAt = rs.getObject("cancelled_at", OffsetDateTime.class);
        return new Appointment(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getObject("employee_id", UUID.class), rs.getObject("service_id", UUID.class),
                new TimeSlot(instant(rs, "start_at"), instant(rs, "end_at")),
                AppointmentStatus.valueOf(rs.getString("status")), rs.getObject("booked_by_user_id", UUID.class),
                rs.getObject("cancelled_by_user_id", UUID.class), cancelledAt == null ? null : cancelledAt.toInstant(),
                rs.getLong("version"));
    }
}
