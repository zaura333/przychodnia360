package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.ClinicCoreIntegrationTest;

/** Bramka RLS z p360-test-support plus oczekiwania specyficzne dla scheduling (brak zakresu platformy, przekaźnik). */
@ClinicCoreIntegrationTest
class RlsCoverageTest extends RlsCoverageContract {

    @Override
    protected Set<String> tablesWithoutTenantId() {
        return Set.of(
                "flyway_schema_history"  // metadane migracji (niedostępne dla p360_app)
        );
    }

    /** Zakres platformy (app_scope) należy do iam: żadna tabela scheduling go nie zna (S3). */
    @Test
    void noTableKnowsPlatformScope() {
        assertThat(tablesWithPolicyReferencing("app_scope")).isEmpty();
    }

    /** Dostęp przekaźnika (app_relay) do wierszy wszystkich placówek ma wyłącznie outbox. */
    @Test
    void onlyOutboxKnowsRelayScope() {
        assertThat(tablesWithPolicyReferencing("app_relay")).containsExactly("outbox");
    }
}
