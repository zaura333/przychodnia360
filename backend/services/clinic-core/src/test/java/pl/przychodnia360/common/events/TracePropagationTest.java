package pl.przychodnia360.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.RecordingEventTransport;

/**
 * Fala 1.6: jeden traceId łączy żądanie HTTP, zdarzenie w outboxie, kopertę wysłaną przez przekaźnik i obsługę
 * zdarzenia przez konsumenta (W3C traceparent). Droga przez prawdziwe SNS i SQS: SnsSqsTransportIntegrationTest.
 */
class TracePropagationTest extends AbstractApiTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_SPAN = "00f067aa0ba902b7";

    @Autowired Tracer tracer;
    @Autowired EventTracing eventTracing;
    @Autowired OutboxRelay relay;
    @Autowired RecordingEventTransport transport;

    @AfterEach
    void recover() {
        transport.recover();
    }

    private void drain() {
        int guard = 0;
        while (relay.runOnce() > 0 && guard++ < 100) {
            // opróżnij kolejkę outboxu
        }
    }

    private <T> T inKnownTrace(java.util.function.Supplier<T> action) {
        TraceContext context = tracer.traceContextBuilder().traceId(TRACE_ID).spanId(PARENT_SPAN).sampled(true).build();
        Span span = tracer.spanBuilder().setParent(context).name("test").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return action.get();
        } finally {
            span.end();
        }
    }

    private String outboxTraceparent(UUID tenant) {
        return inTenantResult(tenant, () -> jdbc.sql("SELECT traceparent FROM outbox ORDER BY created_at DESC LIMIT 1")
                .query(String.class).optional().orElse(null));
    }

    @Test
    void traceparentFromTheHttpRequestEndsUpInTheOutbox() {
        UUID tenant = newTenant();
        String email = "trace." + unique() + "@example.test";
        String body = "{\"tenantSlug\":\"" + slugOf(tenant) + "\",\"email\":\"" + email + "\",\"password\":\""
                + PASSWORD + "\",\"firstName\":\"Maria\",\"lastName\":\"Nowak\",\"birthDate\":\"1991-04-12\"}";

        Resp response = call("POST", "/api/v1/auth/register-patient", null, body,
                "traceparent", "00-" + TRACE_ID + "-" + PARENT_SPAN + "-01");

        assertThat(response.status()).isEqualTo(202);
        assertThat(outboxTraceparent(tenant)).as("zdarzenie PatientRegistered niesie ślad żądania")
                .startsWith("00-" + TRACE_ID + "-").endsWith("-01");
    }

    @Test
    void traceparentTravelsFromTheOutboxThroughTheRelayToTheEnvelope() {
        UUID tenant = newTenant();
        UUID patientId = inKnownTrace(() -> inTenantResult(tenant,
                () -> patientFacade.create("Anna", "Nowak", LocalDate.of(1990, 1, 1), null)));

        drain();

        List<EventEnvelope> sent = transport.sent().stream().filter(e -> e.tenantId().equals(tenant)).toList();
        assertThat(sent).singleElement().satisfies(e -> {
            assertThat(e.aggregateId()).isEqualTo(patientId);
            assertThat(e.traceparent()).startsWith("00-" + TRACE_ID + "-");
        });
    }

    @Test
    void eventPublishedWithoutATraceHasNoTraceparent() {
        UUID tenant = newTenant();
        inTenantResult(tenant, () -> patientFacade.create("Jan", "Kowalski", LocalDate.of(1980, 5, 5), null));
        assertThat(outboxTraceparent(tenant)).isNull();
    }

    @Test
    void consumerRunsTheHandlerInTheTraceOfTheEvent() {
        AtomicReference<String> seen = new AtomicReference<>();
        eventTracing.runInContext("00-" + TRACE_ID + "-" + PARENT_SPAN + "-01", "consume PatientRegistered",
                () -> seen.set(tracer.currentSpan().context().traceId()));

        assertThat(seen.get()).isEqualTo(TRACE_ID);
        assertThat(tracer.currentSpan()).as("po obsłudze kontekst jest przywrócony").isNull();
    }

    @Test
    void traceAndSpanIdsAreInTheLogContextWhileTheEventIsHandled() {
        AtomicReference<String> traceInMdc = new AtomicReference<>();
        AtomicReference<String> spanInMdc = new AtomicReference<>();
        eventTracing.runInContext("00-" + TRACE_ID + "-" + PARENT_SPAN + "-01", "consume PatientRegistered", () -> {
            traceInMdc.set(MDC.get("traceId"));
            spanInMdc.set(MDC.get("spanId"));
        });

        assertThat(traceInMdc.get()).as("MDC traceId (korelacja logów)").isEqualTo(TRACE_ID);
        assertThat(spanInMdc.get()).as("MDC spanId: nowy zakres, nie rodzic").isNotBlank().isNotEqualTo(PARENT_SPAN);
        assertThat(MDC.get("traceId")).as("po obsłudze MDC wyczyszczony").isNull();
    }

    @Test
    void invalidTraceparentRunsTheActionUnchanged() {
        AtomicReference<Boolean> ran = new AtomicReference<>(false);
        for (String invalid : new String[] {null, "", "garbage", "00-zzzz-yyyy-01",
                "01-" + TRACE_ID + "-" + PARENT_SPAN + "-01"}) {
            eventTracing.runInContext(invalid, "consume", () -> ran.set(true));
            assertThat(ran.getAndSet(false)).as("akcja wykonana dla %s", invalid).isTrue();
        }
    }
}
