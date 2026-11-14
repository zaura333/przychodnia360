package pl.przychodnia360.staff.api;

import java.util.UUID;

/**
 * Punkt rozszerzenia: moduły znające pracę pracownika (np. wizyty) zgłaszają, ile przyszłych zobowiązań blokuje
 * dezaktywację. Dzięki temu staff nie zależy od appointments (brak cyklu modułów).
 */
public interface EmployeeDeactivationGuard {

    /** Liczba przyszłych zobowiązań pracownika; wartość dodatnia blokuje dezaktywację (409). */
    long blockingFutureWork(UUID employeeId);
}
