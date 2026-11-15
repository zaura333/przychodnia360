package pl.przychodnia360.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.RecordingEventTransport;
import pl.przychodnia360.identity.api.UserDeactivated;

/** Fala 1.3: outbox zapisuje zdarzenie w transakcji, przekaźnik publikuje po kolei, inbox odrzuca duplikaty. */
class OutboxIntegrationTest extends AbstractApiTest {

    @Autowired OutboxRelay relay;
    @Autowired InboxGuard inbox;
    @Autowired RecordingEventTransport transport;

    @AfterEach
    void recover() {
        transport.recover();
    }

    /** Opróżnia kolejkę: wcześniejsze testy zostawiają zdarzenia, a partia przekaźnika jest ograniczona. */
    private void drain() {
        int guard = 0;
        while (relay.runOnce() > 0 && guard++ < 100) {
            // kolejne partie
        }
    }

    private UUID registerPatient(UUID tenant) {
        return inTenantResult(tenant, () -> publishSampleEvent());
    }

    private List<EventEnvelope> sentFor(UUID tenant) {
        return transport.sent().stream().filter(e -> e.tenantId().equals(tenant)).toList();
    }

    private Integer pending(UUID tenant) {
        return inTenantResult(tenant, () -> jdbc.sql("SELECT count(*) FROM outbox WHERE published_at IS NULL")
                .query(Integer.class).single());
    }

    @Test
    void eventIsWrittenToOutboxInTheSameTransactionAndPublishedByRelay() {
        UUID tenant = newTenant();
        UUID patientId = registerPatient(tenant);
        assertThat(pending(tenant)).isEqualTo(1);

        drain();

        assertThat(sentFor(tenant)).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo("UserDeactivated");
            assertThat(e.schemaVersion()).isEqualTo(1);
            assertThat(e.aggregateId()).isEqualTo(patientId);
            assertThat(e.payload().toString()).doesNotContain("Anna", "Nowak");
        });
        assertThat(pending(tenant)).isZero();
        int before = transport.sent().size();
        drain();
        assertThat(transport.sent()).as("opublikowane zdarzenie nie wraca").hasSize(before);
    }

    @Test
    void rolledBackTransactionLeavesNoEvent() {
        UUID tenant = newTenant();
        assertThatThrownBy(() -> inTenant(tenant, () -> {
            publishSampleEvent();
            throw new IllegalStateException("wycofanie");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(pending(tenant)).isZero();
    }

    @Test
    void relayKeepsOrderAndRetriesAfterTransportFailure() {
        UUID tenant = newTenant();
        UUID first = registerPatient(tenant);
        UUID second = registerPatient(tenant);

        transport.failWith(new IllegalStateException("broker niedostępny"));
        assertThat(relay.runOnce()).isZero();
        assertThat(pending(tenant)).as("nic nie opublikowano").isEqualTo(2);
        int attempts = inTenantResult(tenant, () -> jdbc.sql("SELECT max(attempts) FROM outbox").query(Integer.class).single());
        assertThat(attempts).isPositive();

        transport.recover();
        drain();
        assertThat(sentFor(tenant)).extracting(EventEnvelope::aggregateId).containsExactly(first, second);
    }

    @Test
    void tenantsDoNotSeeEachOthersOutbox() {
        UUID a = newTenant();
        UUID b = newTenant();
        registerPatient(a);
        assertThat(pending(a)).isEqualTo(1);
        assertThat(pending(b)).isZero();
    }

    @Test
    void inboxProcessesEachEventOncePerConsumer() {
        UUID tenant = newTenant();
        UUID eventId = UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();
        boolean first = inTenantResult(tenant, () -> inbox.runOnce("notification", eventId, runs::incrementAndGet));
        boolean duplicate = inTenantResult(tenant, () -> inbox.runOnce("notification", eventId, runs::incrementAndGet));
        boolean otherConsumer = inTenantResult(tenant, () -> inbox.runOnce("billing", eventId, runs::incrementAndGet));
        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(otherConsumer).isTrue();
        assertThat(runs).hasValue(2);
    }

    @Test
    void publishedEventsAreRemovedAfterRetentionPeriod() {
        UUID tenant = newTenant();
        registerPatient(tenant);
        drain();
        assertThat(relay.purgePublishedBefore(Instant.now().minusSeconds(3600))).as("świeże zostają").isZero();
        assertThat(relay.purgePublishedBefore(Instant.now().plusSeconds(60))).isPositive();
    }

    @Test
    void publishingWithoutTransactionIsRejected() {
        UUID tenant = newTenant();
        // zdarzenie poza transakcją nie może zostać zapisane (gwarancja atomowości)
        var event = new UserDeactivated(tenant, UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> eventPublisher.publish(event)).isInstanceOf(IllegalStateException.class);
    }

    @Autowired pl.przychodnia360.shared.DomainEventPublisher eventPublisher;
}
