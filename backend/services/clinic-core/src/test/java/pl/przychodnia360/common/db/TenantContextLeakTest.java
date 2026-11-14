package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.AbstractIntegrationTest;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;

/** Pula = 1: kolejne żądanie na tym samym połączeniu nie może widzieć kontekstu poprzedniego. */
class TenantContextLeakTest extends AbstractIntegrationTest {

    @Value("${spring.datasource.url}")
    String url;

    private HikariDataSource pool;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername("p360_app");
        config.setPassword(appPassword());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        pool = new HikariDataSource(config);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        pool.close();
    }

    @Test
    void contextDoesNotSurviveTransactionOnTheSameConnection() {
        JdbcClient jdbc = JdbcClient.create(pool);
        TransactionTemplate tx = new TransactionTemplate(new TenantAwareTransactionManager(pool));
        TenantId a = new TenantId(UUID.randomUUID());

        // żądanie 1: kontekst A, zapis i odczyt
        try (var h = TenantContext.open(a)) {
            int seen = tx.execute(status -> {
                jdbc.sql("INSERT INTO _tenant_probe (tenant_id, payload) VALUES (?, 'A')").param(a.value()).update();
                return jdbc.sql("SELECT count(*) FROM _tenant_probe").query(Integer.class).single();
            });
            assertThat(seen).isEqualTo(1);
        }

        // żądanie 2: to samo (jedyne) połączenie, bez kontekstu
        assertThat(jdbc.sql("SELECT count(*) FROM _tenant_probe").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT coalesce(current_setting('app.tenant_id', true), '')").query(String.class).single())
                .isEmpty();

        // sprzątanie w kontekście A
        try (var h = TenantContext.open(a)) {
            tx.execute(status -> jdbc.sql("DELETE FROM _tenant_probe").update());
        }
    }
}
