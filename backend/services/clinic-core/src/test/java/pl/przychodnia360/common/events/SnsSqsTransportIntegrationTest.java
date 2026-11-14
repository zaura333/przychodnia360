package pl.przychodnia360.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.localstack.LocalStackContainer;
import pl.przychodnia360.AbstractApiTest;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fala 1.4: pełna droga zdarzenia przez prawdziwe SNS i SQS (LocalStack): outbox -> przekaźnik -> temat SNS ->
 * kolejka SQS -> konsument z inboxem. Sprawdza też duplikat dostawy i wiadomość, która kończy w DLQ.
 */
class SnsSqsTransportIntegrationTest extends AbstractApiTest {

    private static LocalStackContainer localstack;
    private static SnsClient sns;
    private static SqsClient sqs;

    @Autowired JsonMapper mapper;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired InboxGuard inbox;

    /** Handler zapamiętujący odebrane zdarzenia PatientRegistered. */
    private static class Recorder implements EventHandler {
        final List<EventEnvelope> received = new CopyOnWriteArrayList<>();

        @Override
        public String eventType() {
            return "PatientRegistered";
        }

        @Override
        public void handle(EventEnvelope event) {
            received.add(event);
        }
    }

    private record Infra(String topicArn, String queueUrl, String dlqUrl) {
    }

    @BeforeAll
    static void startLocalStack() {
        localstack = new LocalStackContainer("localstack/localstack:3.8.1").withServices("sns", "sqs");
        localstack.start();
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
        URI endpoint = localstack.getEndpoint();
        sns = SnsClient.builder().endpointOverride(endpoint).region(Region.of(localstack.getRegion()))
                .credentialsProvider(credentials).build();
        sqs = SqsClient.builder().endpointOverride(endpoint).region(Region.of(localstack.getRegion()))
                .credentialsProvider(credentials).build();
    }

    @AfterAll
    static void stopLocalStack() {
        if (localstack != null) {
            localstack.stop();
        }
    }

    /** Temat, kolejka z DLQ (druga próba odbioru trafia do DLQ) i subskrypcja z dostawą surową. */
    private Infra infra() {
        String suffix = unique();
        String topicArn = sns.createTopic(b -> b.name("events-" + suffix)).topicArn();
        String dlqUrl = sqs.createQueue(b -> b.queueName("dlq-" + suffix)).queueUrl();
        String dlqArn = sqs.getQueueAttributes(b -> b.queueUrl(dlqUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
        String redrive = "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"2\"}";
        String queueUrl = sqs.createQueue(b -> b.queueName("queue-" + suffix).attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "1",
                QueueAttributeName.REDRIVE_POLICY, redrive))).queueUrl();
        String queueArn = sqs.getQueueAttributes(b -> b.queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
        sns.subscribe(b -> b.topicArn(topicArn).protocol("sqs").endpoint(queueArn)
                .attributes(Map.of("RawMessageDelivery", "true")));
        return new Infra(topicArn, queueUrl, dlqUrl);
    }

    private SqsEventConsumer consumer(Infra infra, Recorder recorder) {
        return new SqsEventConsumer(sqs, infra.queueUrl(), "test-consumer", mapper, inbox, transactionManager,
                List.of(recorder), 1);
    }

    private static void await(BooleanSupplier condition, int seconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
    }

    @Test
    void eventTravelsFromOutboxThroughSnsAndSqsToTheConsumer() throws InterruptedException {
        Infra infra = infra();
        UUID tenant = newTenant();
        UUID patientId = inTenantResult(tenant,
                () -> patientFacade.create("Anna", "Nowak", LocalDate.of(1990, 1, 1), null));

        var relay = new OutboxRelay(jdbc, transactionManager, new SnsEventTransport(sns, infra.topicArn(), mapper),
                mapper, 50);
        int guard = 0;
        while (relay.runOnce() > 0 && guard++ < 100) {
            // opróżnij kolejkę outboxu (wcześniejsze testy zostawiają zdarzenia)
        }

        Recorder recorder = new Recorder();
        SqsEventConsumer consumer = consumer(infra, recorder);
        await(() -> {
            consumer.pollOnce();
            return recorder.received.stream().anyMatch(e -> e.tenantId().equals(tenant));
        }, 20);

        assertThat(recorder.received).filteredOn(e -> e.tenantId().equals(tenant)).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo("PatientRegistered");
            assertThat(e.aggregateId()).isEqualTo(patientId);
            assertThat(e.payload().toString()).doesNotContain("Anna", "Nowak");
        });
        Integer processed = inTenantResult(tenant, () -> jdbc.sql("SELECT count(*) FROM processed_events")
                .query(Integer.class).single());
        assertThat(processed).as("inbox zapisał dostawę").isEqualTo(1);
    }

    @Test
    void duplicateDeliveryIsHandledOnce() throws InterruptedException {
        Infra infra = infra();
        UUID tenant = newTenant();
        var transport = new SnsEventTransport(sns, infra.topicArn(), mapper);
        var envelope = new EventEnvelope(UUID.randomUUID(), "PatientRegistered", 1, tenant, UUID.randomUUID(),
                Instant.now(), null, mapper.readTree("{}"));
        transport.publish(envelope);
        transport.publish(envelope);

        Recorder recorder = new Recorder();
        SqsEventConsumer consumer = consumer(infra, recorder);
        int[] acknowledged = {0};
        await(() -> {
            acknowledged[0] += consumer.pollOnce();
            return acknowledged[0] >= 2;
        }, 20);

        assertThat(acknowledged[0]).as("obie dostawy potwierdzone").isEqualTo(2);
        assertThat(recorder.received).as("handler uruchomiony raz").hasSize(1);
    }

    @Test
    void poisonMessageEndsInTheDeadLetterQueue() throws InterruptedException {
        Infra infra = infra();
        sns.publish(b -> b.topicArn(infra.topicArn()).message("{\"type\":\"PatientRegistered\"}"));

        Recorder recorder = new Recorder();
        SqsEventConsumer consumer = consumer(infra, recorder);
        // dwa nieudane odbiory (maxReceiveCount = 2), każdy po upływie czasu widoczności
        for (int i = 0; i < 3; i++) {
            consumer.pollOnce();
            Thread.sleep(1200);
        }
        var inDlq = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(infra.dlqUrl())
                .waitTimeSeconds(2).maxNumberOfMessages(10).build()).messages();

        assertThat(recorder.received).isEmpty();
        assertThat(inDlq).as("wiadomość bez tenantId trafiła do DLQ").hasSize(1);
    }
}
