package pl.przychodnia360.common.events;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Konsument kolejki SQS (jedna kolejka na konsumenta, subskrypcja tematu SNS z dostawą surową). Każda wiadomość:
 * parsowana jako koperta, wykonywana w kontekście swojej placówki, w jednej transakcji z wpisem inboxu i skutkami
 * obsługi, a dopiero potem kasowana z kolejki. Dostarczanie jest at-least-once, więc duplikat jest pomijany przez
 * {@link InboxGuard}. Wiadomość nieparsowalna, bez {@code tenantId} lub kończąca się wyjątkiem NIE jest kasowana:
 * wraca po upływie czasu widoczności i po przekroczeniu {@code maxReceiveCount} trafia do DLQ kolejki.
 * Typy bez handlera są potwierdzane i pomijane (temat jest rozgłoszeniowy).
 */
public class SqsEventConsumer implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(SqsEventConsumer.class);

    private final SqsClient sqs;
    private final String queueUrl;
    private final String consumerName;
    private final JsonMapper mapper;
    private final InboxGuard inbox;
    private final TransactionTemplate tx;
    private final Map<String, EventHandler> handlers;
    private final int waitSeconds;
    private volatile boolean running;
    private Thread worker;

    public SqsEventConsumer(SqsClient sqs, String queueUrl, String consumerName, JsonMapper mapper, InboxGuard inbox,
            PlatformTransactionManager transactionManager, List<EventHandler> handlers, int waitSeconds) {
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.consumerName = consumerName;
        this.mapper = mapper;
        this.inbox = inbox;
        this.tx = new TransactionTemplate(transactionManager);
        this.handlers = handlers.stream().collect(Collectors.toMap(EventHandler::eventType, Function.identity()));
        this.waitSeconds = waitSeconds;
    }

    /** Jedno odpytanie kolejki; zwraca liczbę potwierdzonych (skasowanych) wiadomości. */
    public int pollOnce() {
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl)
                .maxNumberOfMessages(10).waitTimeSeconds(waitSeconds).build()).messages();
        int acknowledged = 0;
        for (Message message : messages) {
            try {
                process(message);
                sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle()).build());
                acknowledged++;
            } catch (RuntimeException e) {
                LOG.warn("Wiadomość {} nie została przetworzona (wróci do kolejki, potem do DLQ): {}",
                        message.messageId(), e.toString());
            }
        }
        return acknowledged;
    }

    private void process(Message message) {
        EventEnvelope envelope = mapper.readValue(message.body(), EventEnvelope.class);
        if (envelope.tenantId() == null || envelope.eventId() == null || envelope.type() == null) {
            throw new IllegalArgumentException("Koperta bez eventId, type lub tenantId");
        }
        EventHandler handler = handlers.get(envelope.type());
        if (handler == null) {
            return;
        }
        try (var scope = TenantContext.open(new TenantId(envelope.tenantId()))) {
            tx.executeWithoutResult(status -> inbox.runOnce(consumerName, envelope.eventId(),
                    () -> handler.handle(envelope)));
        }
    }

    @Override
    public void start() {
        running = true;
        worker = new Thread(() -> {
            while (running) {
                try {
                    pollOnce();
                } catch (RuntimeException e) {
                    LOG.warn("Błąd odpytywania kolejki {}: {}", queueUrl, e.toString());
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "sqs-" + consumerName);
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
