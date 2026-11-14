package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.ClinicCoreIntegrationTest;

/** Bramka RLS z p360-test-support plus oczekiwania specyficzne dla clinic-core (zakresy platform i przekaźnik). */
@ClinicCoreIntegrationTest
class RlsCoverageTest extends RlsCoverageContract {

    @Override
    protected Set<String> tablesWithoutTenantId() {
        return Set.of(
                "tenants",               // słownik placówek, publiczna lista
                "flyway_schema_history"  // metadane migracji (niedostępne dla p360_app)
        );
    }

    /** Zakres platformy (app_scope) znają wyłącznie tabele tożsamości; żadna tabela danych placówki go nie zna. */
    @Test
    void onlyIdentityTablesKnowPlatformScope() {
        assertThat(tablesWithPolicyReferencing("app_scope"))
                .containsExactlyInAnyOrder("users", "refresh_tokens", "password_reset_tokens");
    }

    /** Dostęp przekaźnika (app_relay) do wierszy wszystkich placówek ma wyłącznie outbox. */
    @Test
    void onlyOutboxKnowsRelayScope() {
        assertThat(tablesWithPolicyReferencing("app_relay")).containsExactly("outbox");
    }
}
