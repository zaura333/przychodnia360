package pl.przychodnia360.common.events;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pl.przychodnia360.shared.TenantContext;

/**
 * Inbox (idempotencja konsumenta): zdarzenie o danym {@code eventId} jest przetwarzane przez danego konsumenta
 * co najwyżej raz. Wołać wewnątrz transakcji z ustawionym kontekstem placówki zdarzenia: wpis w
 * {@code processed_events} i skutki obsługi zatwierdzają się razem.
 */
@Component
public class InboxGuard {

    private final JdbcClient jdbc;

    InboxGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Uruchamia akcję tylko przy pierwszej dostawie; zwraca false dla duplikatu. */
    public boolean runOnce(String consumer, UUID eventId, Runnable action) {
        int inserted = jdbc.sql("""
                INSERT INTO processed_events (consumer, event_id, tenant_id) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """).params(consumer, eventId, TenantContext.requireTenant().value()).update();
        if (inserted == 0) {
            return false;
        }
        action.run();
        return true;
    }
}
