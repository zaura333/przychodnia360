package pl.przychodnia360.common.db;

import java.util.Map;
import pl.przychodnia360.ClinicCoreIntegrationTest;

/** Mapa tabela -> moduł scheduling (iam, catalog i patient są osobnymi usługami) dla bramki z p360-test-support. */
@ClinicCoreIntegrationTest
class ModuleBoundariesFkTest extends ModuleBoundariesFkContract {

    @Override
    protected Map<String, String> moduleOf() {
        return Map.ofEntries(
                Map.entry("employees", "scheduling"),
                Map.entry("work_schedules", "scheduling"),
                Map.entry("schedule_exceptions", "scheduling"),
                Map.entry("appointments", "scheduling"),
                // tabele platformowe (starter): bez kluczy obcych, kopiowane do każdej usługi
                Map.entry("outbox", "platform"),
                Map.entry("processed_events", "platform"));
    }
}
