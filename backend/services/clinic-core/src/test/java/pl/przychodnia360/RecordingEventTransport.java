package pl.przychodnia360;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pl.przychodnia360.common.events.EventEnvelope;
import pl.przychodnia360.common.events.EventTransport;

/** Transport testowy: zapamiętuje koperty i pozwala wymusić awarię wysyłki. */
public class RecordingEventTransport implements EventTransport {

    private final List<EventEnvelope> sent = new CopyOnWriteArrayList<>();
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

    @Override
    public void publish(EventEnvelope envelope) {
        RuntimeException e = failure.get();
        if (e != null) {
            throw e;
        }
        sent.add(envelope);
    }

    public List<EventEnvelope> sent() {
        return sent;
    }

    public void failWith(RuntimeException e) {
        failure.set(e);
    }

    public void recover() {
        failure.set(null);
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        RecordingEventTransport recordingEventTransport() {
            return new RecordingEventTransport();
        }
    }
}
