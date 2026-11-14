package pl.przychodnia360.common.events;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Ślad (W3C Trace Context) w zdarzeniach: publikujący zapisuje bieżący {@code traceparent} razem ze zdarzeniem
 * (outbox, koperta), a konsument odtwarza go jako rodzica nowego zakresu, więc jeden {@code traceId} łączy żądanie
 * HTTP, zdarzenie i jego obsługę w innej usłudze. Bez skonfigurowanego trasowania (brak {@link Tracer}) wszystko
 * działa jak dotąd: {@code traceparent} jest null.
 */
@Component
public class EventTracing {

    private static final Pattern TRACEPARENT = Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");

    private final ObjectProvider<Tracer> tracers;

    EventTracing(ObjectProvider<Tracer> tracers) {
        this.tracers = tracers;
    }

    /** {@code traceparent} bieżącego zakresu albo null, gdy nie ma śladu. */
    public String currentTraceparent() {
        Tracer tracer = tracers.getIfAvailable();
        Span span = tracer == null ? null : tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        String traceId = pad(context.traceId(), 32);
        String spanId = pad(context.spanId(), 16);
        if (isZero(traceId) || isZero(spanId)) {
            return null;
        }
        return "00-" + traceId + "-" + spanId + "-" + (Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    }

    /**
     * Wykonuje akcję w nowym zakresie, którego rodzicem jest kontekst z {@code traceparent}. Niepoprawny lub pusty
     * {@code traceparent} (albo brak trasowania) oznacza zwykłe wykonanie bez zmiany kontekstu.
     */
    public void runInContext(String traceparent, String spanName, Runnable action) {
        Tracer tracer = tracers.getIfAvailable();
        Matcher matcher = traceparent == null ? null : TRACEPARENT.matcher(traceparent);
        if (tracer == null || matcher == null || !matcher.matches()) {
            action.run();
            return;
        }
        boolean sampled = (Integer.parseInt(matcher.group(3), 16) & 1) == 1;
        TraceContext parent = tracer.traceContextBuilder().traceId(matcher.group(1)).spanId(matcher.group(2))
                .sampled(sampled).build();
        Span span = tracer.spanBuilder().setParent(parent).name(spanName).kind(Span.Kind.CONSUMER).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            action.run();
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private static String pad(String hex, int length) {
        return hex.length() >= length ? hex : "0".repeat(length - hex.length()) + hex;
    }

    private static boolean isZero(String hex) {
        return hex.chars().allMatch(c -> c == '0');
    }
}
