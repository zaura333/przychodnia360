package pl.przychodnia360.shared;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Deterministyczne identyfikatory danych demonstracyjnych (NFR-07). Każda usługa seeduje własną bazę, a te same
 * nazwy dają te same UUID, więc placówki, konta, pracownicy, pacjenci i usługi zgadzają się między usługami bez
 * wywołań sieciowych w trakcie startu.
 */
public final class DemoIds {

    private DemoIds() {
    }

    public static UUID tenant(String slug) {
        return of("tenant", slug);
    }

    public static UUID user(String slug, String login) {
        return of("user", slug, login);
    }

    public static UUID employee(String slug, String login) {
        return of("employee", slug, login);
    }

    public static UUID patient(String slug, String key) {
        return of("patient", slug, key);
    }

    public static UUID category(String slug, String key) {
        return of("category", slug, key);
    }

    public static UUID service(String slug, String key) {
        return of("service", slug, key);
    }

    public static UUID appointment(String slug, String key) {
        return of("appointment", slug, key);
    }

    private static UUID of(String... parts) {
        return UUID.nameUUIDFromBytes(("p360-demo:" + String.join("/", parts)).getBytes(StandardCharsets.UTF_8));
    }
}
