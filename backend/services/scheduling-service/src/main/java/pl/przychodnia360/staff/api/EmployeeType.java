package pl.przychodnia360.staff.api;

import pl.przychodnia360.shared.PerformerType;

public enum EmployeeType {
    DOCTOR, MEDICAL_STAFF, RECEPTIONIST;

    /** FR-57: usługę wykonuje pracownik typu zgodnego z typem wykonawcy usługi (rejestrator nie wykonuje usług). */
    public boolean canPerform(PerformerType performerType) {
        return switch (this) {
            case DOCTOR -> performerType == PerformerType.DOCTOR;
            case MEDICAL_STAFF -> performerType == PerformerType.MEDICAL_STAFF;
            case RECEPTIONIST -> false;
        };
    }
}
