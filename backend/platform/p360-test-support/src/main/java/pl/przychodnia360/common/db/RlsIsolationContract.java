package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.testsupport.PostgresIntegrationTest;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;

/** Wzorcowy test RLS na tabeli _tenant_probe: dwie placówki, izolacja odczytu, zapisu i brak kontekstu. */
public abstract class RlsIsolationContract extends PostgresIntegrationTest {

    @Autowired
    DataSource dataSource;
    @Autowired
    PlatformTransactionManager txManager;

    private JdbcClient jdbc;
    private TransactionTemplate tx;
    private TenantId a;
    private TenantId b;

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        tx = new TransactionTemplate(txManager);
        a = new TenantId(UUID.randomUUID());
        b = new TenantId(UUID.randomUUID());
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        for (TenantId t : new TenantId[] {a, b}) {
            inTenant(t, () -> jdbc.sql("DELETE FROM _tenant_probe").update());
        }
    }

    private <T> T inTenant(TenantId tenant, java.util.function.Supplier<T> work) {
        try (var h = TenantContext.open(tenant)) {
            return tx.execute(status -> work.get());
        }
    }

    private void insert(TenantId owner, String payload) {
        inTenant(owner, () -> jdbc.sql("INSERT INTO _tenant_probe (tenant_id, payload) VALUES (?, ?)")
                .param(owner.value()).param(payload).update());
    }

    private int count() {
        return jdbc.sql("SELECT count(*) FROM _tenant_probe").query(Integer.class).single();
    }

    @Test
    void tenantSeesOnlyOwnRows() {
        insert(a, "wiersz A");
        insert(b, "wiersz B");

        assertThat(inTenant(a, this::count)).isEqualTo(1);
        assertThat(inTenant(b, this::count)).isEqualTo(1);
        assertThat(inTenant(a, () -> jdbc.sql("SELECT payload FROM _tenant_probe").query(String.class).single()))
                .isEqualTo("wiersz A");
    }

    @Test
    void otherTenantCannotUpdateOrDelete() {
        insert(a, "wiersz A");

        int updated = inTenant(b, () -> jdbc.sql("UPDATE _tenant_probe SET payload = 'x'").update());
        int deleted = inTenant(b, () -> jdbc.sql("DELETE FROM _tenant_probe").update());
        assertThat(updated).isZero();
        assertThat(deleted).isZero();
        assertThat(inTenant(a, this::count)).isEqualTo(1);
    }

    @Test
    void insertWithForeignTenantIdIsRejectedByWithCheck() {
        assertThatThrownBy(() -> inTenant(a, () -> jdbc.sql("INSERT INTO _tenant_probe (tenant_id, payload) VALUES (?, ?)")
                .param(b.value()).param("podszywka").update()))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void noContextMeansNoRows() {
        insert(a, "wiersz A");

        // jawny brak placówki w transakcji
        try (var h = TenantContext.openWithoutTenant()) {
            Integer inTx = tx.execute(status -> count());
            assertThat(inTx).isZero();
        }
        // zapytanie poza transakcją (autocommit, bez kontekstu)
        assertThat(count()).isZero();
    }

    @Test
    void transactionWithoutAnyContextIsRejected() {
        assertThatThrownBy(() -> tx.execute(status -> count()))
                .isInstanceOf(TenantAwareTransactionManager.MissingTenantContextException.class);
    }
}
