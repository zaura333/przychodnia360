package pl.przychodnia360.common.events;

import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.przychodnia360.shared.DomainEvent;
import pl.przychodnia360.shared.DomainEventPublisher;
import pl.przychodnia360.shared.Ids;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transakcyjny outbox (ADR-M03): zdarzenie trafia do tabeli {@code outbox} w tej samej transakcji co zmiana danych,
 * więc nie ginie przy awarii i nie powstaje przy wycofaniu transakcji. Dostarczeniem do brokera zajmuje się
 * {@link OutboxRelay}. Dodatkowo, po commit, zdarzenie trafia do nasłuchujących w procesie (np. odwołanie dostępu).
 */
@Primary
@Component
class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final JdbcClient jdbc;
    private final JsonMapper mapper;
    private final InProcessDomainEventPublisher inProcess;

    OutboxDomainEventPublisher(JdbcClient jdbc, JsonMapper mapper, InProcessDomainEventPublisher inProcess) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.inProcess = inProcess;
    }

    @Override
    public void publish(DomainEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Zdarzenie " + event.type() + " wymaga aktywnej transakcji (outbox)");
        }
        if (event.tenantId() == null) {
            throw new IllegalArgumentException("Zdarzenie " + event.type() + " bez tenantId");
        }
        jdbc.sql("""
                INSERT INTO outbox (id, tenant_id, aggregate_id, type, schema_version, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """)
                .params(Ids.newId(), event.tenantId(), event.aggregateId(), event.type(), event.schemaVersion(),
                        mapper.writeValueAsString(event), java.sql.Timestamp.from(event.occurredAt()))
                .update();
        inProcess.publish(event);
    }
}
