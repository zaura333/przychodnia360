package pl.przychodnia360.common.events;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.shared.TenantContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Przekaźnik outboxu: w jednej transakcji bierze nieopublikowane zdarzenia (w kolejności zapisu), wysyła je
 * {@link EventTransport} i oznacza jako opublikowane. Pojedyncza instancja pracuje naraz dzięki
 * {@code pg_try_advisory_xact_lock}. Przy błędzie wysyłki zatrzymuje partię (zachowanie kolejności), zwiększa
 * licznik prób i zapisuje błąd; zdarzenie zostanie ponowione w następnym przebiegu. Dostarczanie jest
 * at-least-once, więc konsumenci muszą być idempotentni ({@link InboxGuard}).
 *
 * <p>Dostęp do wierszy wszystkich placówek daje ustawienie {@code app.relay = 'on'} (polityka RLS relay_scope).
 */
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    /** Klucz blokady doradczej przekaźnika outboxu. */
    private static final long LOCK_KEY = 0x70333630_6f757462L;

    private record Row(UUID id, UUID tenantId, UUID aggregateId, String type, int schemaVersion, String payload,
            java.time.Instant occurredAt, String traceparent) {
    }

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final EventTransport transport;
    private final JsonMapper mapper;
    private final int batchSize;

    public OutboxRelay(JdbcClient jdbc, PlatformTransactionManager transactionManager, EventTransport transport,
            JsonMapper mapper, int batchSize) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.transport = transport;
        this.mapper = mapper;
        this.batchSize = batchSize;
    }

    /** Jeden przebieg; zwraca liczbę opublikowanych zdarzeń. */
    public int runOnce() {
        try (var scope = TenantContext.openWithoutTenant()) {
            Integer published = tx.execute(status -> publishBatch());
            return published == null ? 0 : published;
        }
    }

    private int publishBatch() {
        boolean locked = Boolean.TRUE.equals(
                jdbc.sql("SELECT pg_try_advisory_xact_lock(?)").param(LOCK_KEY).query(Boolean.class).single());
        if (!locked) {
            return 0;
        }
        jdbc.sql("SELECT set_config('app.relay', 'on', true)").query(String.class).single();
        List<Row> rows = jdbc.sql("""
                SELECT id, tenant_id, aggregate_id, type, schema_version, payload::text AS payload, occurred_at, traceparent
                  FROM outbox WHERE published_at IS NULL ORDER BY created_at, id LIMIT ? FOR UPDATE
                """).param(batchSize).query((rs, n) -> new Row(rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class), rs.getObject("aggregate_id", UUID.class),
                rs.getString("type"), rs.getInt("schema_version"), rs.getString("payload"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getString("traceparent"))).list();
        int published = 0;
        for (Row row : rows) {
            try {
                JsonNode payload = mapper.readTree(row.payload());
                transport.publish(new EventEnvelope(row.id(), row.type(), row.schemaVersion(), row.tenantId(),
                        row.aggregateId(), row.occurredAt(), row.traceparent(), payload));
                jdbc.sql("UPDATE outbox SET published_at = now(), attempts = attempts + 1, last_error = NULL WHERE id = ?")
                        .param(row.id()).update();
                published++;
            } catch (RuntimeException e) {
                LOG.warn("Nie udało się wysłać zdarzenia {} {}: {}", row.type(), row.id(), e.toString());
                jdbc.sql("UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE id = ?")
                        .param(truncate(e.toString())).param(row.id()).update();
                break;
            }
        }
        return published;
    }

    /** Usuwa opublikowane zdarzenia starsze niż podany próg (klasa retencji RC-10); zwraca liczbę usuniętych. */
    public int purgePublishedBefore(java.time.Instant threshold) {
        try (var scope = TenantContext.openWithoutTenant()) {
            Integer deleted = tx.execute(status -> {
                jdbc.sql("SELECT set_config('app.relay', 'on', true)").query(String.class).single();
                return jdbc.sql("DELETE FROM outbox WHERE published_at IS NOT NULL AND published_at < ?")
                        .param(Timestamp.from(threshold)).update();
            });
            return deleted == null ? 0 : deleted;
        }
    }

    private static String truncate(String text) {
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
