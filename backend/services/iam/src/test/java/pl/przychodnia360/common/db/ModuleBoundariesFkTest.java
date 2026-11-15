package pl.przychodnia360.common.db;

import java.util.Map;
import pl.przychodnia360.IamIntegrationTest;

/** Mapa tabela -> moduł iam (tabele usługi i platformowe) dla bramki z p360-test-support. */
@IamIntegrationTest
class ModuleBoundariesFkTest extends ModuleBoundariesFkContract {

    @Override
    protected Map<String, String> moduleOf() {
        return Map.ofEntries(
                Map.entry("tenants", "iam"),
                Map.entry("users", "iam"),
                Map.entry("refresh_tokens", "iam"),
                Map.entry("password_reset_tokens", "iam"),
                // tabele platformowe (starter): bez kluczy obcych, kopiowane do każdej usługi
                Map.entry("outbox", "platform"),
                Map.entry("processed_events", "platform"));
    }
}
