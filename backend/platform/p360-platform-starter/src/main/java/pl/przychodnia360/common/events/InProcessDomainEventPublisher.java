package pl.przychodnia360.common.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.przychodnia360.shared.DomainEvent;
import pl.przychodnia360.shared.DomainEventPublisher;

/** Zdarzenia w procesie: Spring {@link ApplicationEventPublisher}, ale dopiero po commit transakcji. */
@Component
class InProcessDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    InProcessDomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(DomainEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delegate.publishEvent(event);
                }
            });
        } else {
            delegate.publishEvent(event);
        }
    }
}
