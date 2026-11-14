package pl.przychodnia360.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.przychodnia360.shared.DomainEvent;

class InProcessDomainEventPublisherTest {

    record TestEvent(UUID tenantId, UUID aggregateId, Instant occurredAt) implements DomainEvent {
    }

    private final List<Object> published = new ArrayList<>();
    private final InProcessDomainEventPublisher publisher = new InProcessDomainEventPublisher(published::add);
    private final TestEvent event = new TestEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.now());

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesImmediatelyWithoutTransaction() {
        publisher.publish(event);
        assertThat(published).containsExactly(event);
        assertThat(event.type()).isEqualTo("TestEvent");
    }

    @Test
    void publishesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        publisher.publish(event);
        assertThat(published).as("przed commit").isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(published).containsExactly(event);
    }

    @Test
    void doesNotPublishWhenTransactionIsRolledBack() {
        TransactionSynchronizationManager.initSynchronization();
        publisher.publish(event);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(published).isEmpty();
    }
}
