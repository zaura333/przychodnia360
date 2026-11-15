package pl.przychodnia360.appointments.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pl.przychodnia360.appointments.api.AppointmentViews.AppointmentView;
import pl.przychodnia360.appointments.api.AppointmentViews.StaffAppointmentView;
import pl.przychodnia360.appointments.domain.Appointment;
import pl.przychodnia360.remote.CatalogFacade;
import pl.przychodnia360.remote.CatalogFacade.ServiceInfo;
import pl.przychodnia360.remote.PatientFacade;
import pl.przychodnia360.remote.PatientFacade.PatientInfo;
import pl.przychodnia360.staff.api.StaffFacade;
import pl.przychodnia360.staff.api.StaffFacade.EmployeeInfo;

/** Składa widoki wizyt z nazwami usług, wykonawców i pacjentów pobranymi przez fasady (bez łączenia tabel). */
@Component
class ViewAssembler {

    private final CatalogFacade catalog;
    private final StaffFacade staff;
    private final PatientFacade patients;

    ViewAssembler(CatalogFacade catalog, StaffFacade staff, PatientFacade patients) {
        this.catalog = catalog;
        this.staff = staff;
        this.patients = patients;
    }

    List<AppointmentView> patientViews(Collection<Appointment> appointments) {
        Map<UUID, ServiceInfo> services = catalog.services(ids(appointments, Appointment::serviceId));
        Map<UUID, EmployeeInfo> employees = staff.employees(ids(appointments, Appointment::employeeId));
        return appointments.stream().map(a -> new AppointmentView(a.id(), a.startAt(), a.endAt(), a.serviceId(),
                name(services.get(a.serviceId())), a.employeeId(), name(employees.get(a.employeeId())), a.status()))
                .toList();
    }

    List<StaffAppointmentView> staffViews(Collection<Appointment> appointments) {
        Map<UUID, ServiceInfo> services = catalog.services(ids(appointments, Appointment::serviceId));
        Map<UUID, EmployeeInfo> employees = staff.employees(ids(appointments, Appointment::employeeId));
        Map<UUID, PatientInfo> patientInfo = patients.findAll(ids(appointments, Appointment::patientId));
        return appointments.stream().map(a -> new StaffAppointmentView(a.id(), a.startAt(), a.endAt(), a.serviceId(),
                name(services.get(a.serviceId())), a.employeeId(), name(employees.get(a.employeeId())), a.patientId(),
                name(patientInfo.get(a.patientId())), a.status())).toList();
    }

    private static Collection<UUID> ids(Collection<Appointment> appointments,
            java.util.function.Function<Appointment, UUID> id) {
        return appointments.stream().map(id).collect(Collectors.toSet());
    }

    private static String name(ServiceInfo s) {
        return s == null ? null : s.name();
    }

    private static String name(EmployeeInfo e) {
        return e == null ? null : e.fullName();
    }

    private static String name(PatientInfo p) {
        return p == null ? null : p.fullName();
    }
}
