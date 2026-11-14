package pl.przychodnia360.common.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Konfiguracja przekaźnika outboxu. Cykliczne uruchamianie włącza {@code p360.events.relay.enabled=true}. */
@Configuration
@EnableScheduling
class EventsConfig {

    @Bean
    @ConditionalOnMissingBean(EventTransport.class)
    EventTransport loggingEventTransport() {
        return new LoggingEventTransport();
    }

    @Bean
    OutboxRelay outboxRelay(JdbcClient jdbc, PlatformTransactionManager transactionManager,
            EventTransport transport, JsonMapper mapper,
            @Value("${p360.events.relay.batch-size:50}") int batchSize) {
        return new OutboxRelay(jdbc, transactionManager, transport, mapper, batchSize);
    }

    @Bean
    @ConditionalOnProperty(name = "p360.events.relay.enabled", havingValue = "true")
    OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay) {
        return new OutboxRelayScheduler(relay);
    }

    static class OutboxRelayScheduler {

        private final OutboxRelay relay;

        OutboxRelayScheduler(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${p360.events.relay.interval:PT1S}")
        void tick() {
            relay.runOnce();
        }
    }
}
