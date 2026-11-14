package pl.przychodnia360.shared;

/**
 * Publikacja zdarzeń domenowych. W fali 0R implementacja działa w procesie (po zatwierdzeniu transakcji),
 * w fali 1.3 jej miejsce zajmuje outbox z dostarczaniem do SNS/SQS; moduły nie zmieniają się.
 */
public interface DomainEventPublisher {

    /** Publikuje zdarzenie po zatwierdzeniu bieżącej transakcji (bez transakcji: od razu). */
    void publish(DomainEvent event);
}
