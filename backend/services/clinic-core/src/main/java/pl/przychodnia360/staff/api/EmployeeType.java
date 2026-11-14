package pl.przychodnia360.staff.api;

public enum EmployeeType {
    DOCTOR, MEDICAL_STAFF, RECEPTIONIST;

    /**
     * FR-57: usługę wykonuje pracownik typu zgodnego z typem wykonawcy usługi (rejestrator nie wykonuje usług).
     * Typ wykonawcy to wartość kontraktu katalogu ({@code DOCTOR} lub {@code MEDICAL_STAFF}), a nie klasa z innego
     * modułu (podział na usługi, S1); nieznana wartość nie pasuje do nikogo.
     */
    public boolean canPerform(String performerType) {
        return switch (this) {
            case DOCTOR -> "DOCTOR".equals(performerType);
            case MEDICAL_STAFF -> "MEDICAL_STAFF".equals(performerType);
            case RECEPTIONIST -> false;
        };
    }
}
