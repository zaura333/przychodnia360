package pl.przychodnia360.shared;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Deterministyczne identyfikatory podmiotów kont (pracownik, pacjent) wyliczane z placówki i e-maila. Dzięki nim sagi
 * między usługami (iam, scheduling, patient) są idempotentne: powtórzenie po utraconej odpowiedzi trafia w ten sam
 * rekord, a dane demo w różnych usługach łączą się bez wymiany identyfikatorów.
 */
public final class SubjectIds {

    private SubjectIds() {
    }

    /** Pracownik placówki o danym e-mailu (saga zakładania pracownika: scheduling i iam). */
    public static UUID employee(UUID tenantId, String email) {
        return UUID.nameUUIDFromBytes(("employee:" + tenantId + ":" + normalize(email)).getBytes(StandardCharsets.UTF_8));
    }

    /** Pacjent placówki o danym e-mailu (saga rejestracji pacjenta: iam i patient). */
    public static UUID patient(UUID tenantId, String email) {
        return UUID.nameUUIDFromBytes((tenantId + ":" + normalize(email)).getBytes(StandardCharsets.UTF_8));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
