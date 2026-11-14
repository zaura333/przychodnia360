package pl.przychodnia360.common.db;

import java.util.Map;
import pl.przychodnia360.ClinicCoreIntegrationTest;

/** Mapa tabela -> moduł clinic-core (iam, catalog, patients, scheduling) dla bramki z p360-test-support. */
@ClinicCoreIntegrationTest
class ModuleBoundariesFkTest extends ModuleBoundariesFkContract {

    @Override
    protected Map<String, String> moduleOf() {
        return Map.ofEntries(
                Map.entry("tenants", "iam"),
                Map.entry("users", "iam"),
                Map.entry("refresh_tokens", "iam"),
                Map.entry("password_reset_tokens", "iam"),
                Map.entry("service_categories", "catalog"),
                Map.entry("services", "catalog"),
                Map.entry("patients", "patients"),
                Map.entry("employees", "scheduling"),
                Map.entry("work_schedules", "scheduling"),
                Map.entry("schedule_exceptions", "scheduling"),
                Map.entry("appointments", "scheduling"),
                // tabele platformowe (starter): bez kluczy obcych, kopiowane do każdej usługi
                Map.entry("outbox", "platform"),
                Map.entry("processed_events", "platform"));
    }
}
