package pl.przychodnia360.common.security;

/** Zdolności przypisywane rolom w {@link PermissionPolicy}. Uprawnienia "własne" wymagają dodatkowo kontroli właściciela. */
public enum Permission {
    SERVICE_READ,
    SERVICE_MANAGE,
    EMPLOYEE_MANAGE,
    SCHEDULE_READ,
    SCHEDULE_READ_OWN,
    SCHEDULE_MANAGE,
    SLOT_READ,
    APPOINTMENT_BOOK_OWN,
    APPOINTMENT_BOOK_ANY,
    APPOINTMENT_RESCHEDULE_ANY,
    APPOINTMENT_CANCEL_ANY,
    APPOINTMENT_READ_OWN,
    APPOINTMENT_READ_ALL,
    PATIENT_READ_OWN,
    PATIENT_EDIT_OWN,
    PATIENT_REGISTER,
    PATIENT_CONTACT_EDIT,
    PATIENT_SEARCH,
    USER_MANAGE,
    TENANT_READ,
    PLATFORM_MANAGE,
    /** Wewnętrzny odczyt katalogu przez inne usługi (tylko SERVICE). */
    CATALOG_INTERNAL_READ,
    /** Wewnętrzny odczyt pacjentów przez inne usługi (tylko SERVICE). */
    PATIENT_INTERNAL_READ,
    /** Wewnętrzny zapis pacjentów przez inne usługi, np. saga rejestracji (tylko SERVICE). */
    PATIENT_INTERNAL_WRITE,
    /** Wewnętrzne operacje na kontach w iam, np. saga zakładania pracownika (tylko SERVICE). */
    IDENTITY_INTERNAL_WRITE
}
