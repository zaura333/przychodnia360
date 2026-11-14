package pl.przychodnia360.common.security;

/** Role stałe (ADR-B03). Rola jest wpisana w token dostępu. */
public enum Role {
    PATIENT, DOCTOR, MEDICAL_STAFF, RECEPTIONIST, TENANT_ADMIN, PLATFORM_ADMIN
}
