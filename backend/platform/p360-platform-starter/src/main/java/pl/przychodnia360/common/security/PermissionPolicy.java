package pl.przychodnia360.common.security;

import static pl.przychodnia360.common.security.Permission.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Jedyne miejsce z macierzą uprawnień (ADR-B03). Po rozszerzeniach w 1b-1d tylko dopisujemy wiersze. */
public final class PermissionPolicy {

    private static final Map<Role, Set<Permission>> MATRIX = new EnumMap<>(Role.class);

    static {
        MATRIX.put(Role.PATIENT, EnumSet.of(SERVICE_READ, SCHEDULE_READ, SLOT_READ, APPOINTMENT_BOOK_OWN,
                APPOINTMENT_READ_OWN, PATIENT_READ_OWN, PATIENT_EDIT_OWN, TENANT_READ));
        MATRIX.put(Role.RECEPTIONIST, EnumSet.of(SERVICE_READ, SCHEDULE_READ, SLOT_READ, APPOINTMENT_BOOK_ANY,
                APPOINTMENT_RESCHEDULE_ANY, APPOINTMENT_CANCEL_ANY, APPOINTMENT_READ_ALL, PATIENT_REGISTER,
                PATIENT_CONTACT_EDIT, PATIENT_SEARCH, TENANT_READ));
        MATRIX.put(Role.DOCTOR, EnumSet.of(SERVICE_READ, SCHEDULE_READ, SCHEDULE_READ_OWN, TENANT_READ));
        MATRIX.put(Role.MEDICAL_STAFF, EnumSet.of(SERVICE_READ, SCHEDULE_READ, SCHEDULE_READ_OWN, TENANT_READ));
        MATRIX.put(Role.TENANT_ADMIN, EnumSet.of(SERVICE_READ, SERVICE_MANAGE, EMPLOYEE_MANAGE, SCHEDULE_READ,
                SCHEDULE_MANAGE, APPOINTMENT_READ_ALL, USER_MANAGE, TENANT_READ));
        MATRIX.put(Role.PLATFORM_ADMIN, EnumSet.of(PLATFORM_MANAGE));
    }

    private PermissionPolicy() {
    }

    public static boolean has(Role role, Permission permission) {
        return MATRIX.getOrDefault(role, Set.of()).contains(permission);
    }

    public static boolean has(AuthenticatedUser user, Permission permission) {
        return user != null && has(user.role(), permission);
    }

    /** Kopia macierzy (do testów i dokumentacji). */
    public static Map<Role, Set<Permission>> matrix() {
        Map<Role, Set<Permission>> copy = new EnumMap<>(Role.class);
        MATRIX.forEach((r, p) -> copy.put(r, Set.copyOf(p)));
        return copy;
    }
}
