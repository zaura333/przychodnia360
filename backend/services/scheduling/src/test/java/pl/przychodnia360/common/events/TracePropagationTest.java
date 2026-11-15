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
import pl.przychodnia360.appointments.api.event.AppointmentBooked;
import pl.przychodnia360.appointments.api.event.AppointmentCancelled;

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
        String admin = tokenFor(tenant, pl.przychodnia360.common.security.Role.TENANT_ADMIN);
        String employeeId = call("POST", "/api/v1/admin/employees", admin, "{\"email\":\"lek." + unique()
                + "@example.test\",\"type\":\"DOCTOR\",\"firstName\":\"Adam\",\"lastName\":\"Lekarz\"}")
                .json().get("id").asString();

        String serviceId = fakeCatalog.add(tenant, "Konsultacja", new java.math.BigDecimal("100"), 30, "DOCTOR").toString();
        java.time.LocalDate day = java.time.LocalDate.now().plusDays(3);
        assertThat(call("PUT", "/api/v1/admin/employees/" + employeeId + "/schedule", admin, "{\"entries\":[{\"dayOfWeek\":\""
                + day.getDayOfWeek() + "\",\"startTime\":\"08:00\",\"endTime\":\"16:00\",\"validFrom\":\"2020-01-01\"}]}")
                .status()).isEqualTo(200);
        UUID patientId = newPatientAccount(tenant, "Ewa", "Pacjentka").patientId();
        String startAt = pl.przychodnia360.common.time.ClinicTime.toInstant(day, java.time.LocalTime.of(9, 0)).toString();

        // rezerwacja publikuje AppointmentBooked w żądaniu ze śladem
        Resp response = call("POST", "/api/v1/appointments", tokenFor(tenant, pl.przychodnia360.common.security.Role.RECEPTIONIST),
                "{\"serviceId\":\"" + serviceId + "\",\"employeeId\":\"" + employeeId + "\",\"startAt\":\"" + startAt
                        + "\",\"patientId\":\"" + patientId + "\"}",
                "traceparent", "00-" + TRACE_ID + "-" + PARENT_SPAN + "-01");

        assertThat(response.status()).as(response.body()).isEqualTo(201);
        assertThat(outboxTraceparent(tenant)).as("zdarzenie AppointmentBooked niesie ślad żądania")
                .startsWith("00-" + TRACE_ID + "-").endsWith("-01");
    }

    @Test
    void traceparentTravelsFromTheOutboxThroughTheRelayToTheEnvelope() {
        UUID tenant = newTenant();
        UUID patientId = inKnownTrace(() -> inTenantResult(tenant,
                () -> publishSampleEvent()));

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
        inTenantResult(tenant, () -> publishSampleEvent());
        assertThat(outboxTraceparent(tenant)).isNull();
    }

    @Test
    void consumerRunsTheHandlerInTheTraceOfTheEvent() {
        AtomicReference<String> seen = new AtomicReference<>();
        eventTracing.runInContext("00-" + TRACE_ID + "-" + PARENT_SPAN + "-01", "consume AppointmentCancelled",
                () -> seen.set(tracer.currentSpan().context().traceId()));

        assertThat(seen.get()).isEqualTo(TRACE_ID);
        assertThat(tracer.currentSpan()).as("po obsłudze kontekst jest przywrócony").isNull();
    }

    @Test
    void traceAndSpanIdsAreInTheLogContextWhileTheEventIsHandled() {
        AtomicReference<String> traceInMdc = new AtomicReference<>();
        AtomicReference<String> spanInMdc = new AtomicReference<>();
        eventTracing.runInContext("00-" + TRACE_ID + "-" + PARENT_SPAN + "-01", "consume AppointmentCancelled", () -> {
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
