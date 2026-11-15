package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.CatalogIntegrationTest;

/** Bramka RLS z p360-test-support plus oczekiwania catalog: brak zakresu platformy, przekaźnik tylko w outboxie. */
@CatalogIntegrationTest
class RlsCoverageTest extends RlsCoverageContract {

    @Override
    protected Set<String> tablesWithoutTenantId() {
        return Set.of("flyway_schema_history"); // metadane migracji (niedostępne dla aplikacji)
    }

    @Test
    void noTableKnowsPlatformScope() {
        assertThat(tablesWithPolicyReferencing("app_scope")).isEmpty();
    }

    @Test
    void onlyOutboxKnowsRelayScope() {
        assertThat(tablesWithPolicyReferencing("app_relay")).containsExactly("outbox");
    }
}
