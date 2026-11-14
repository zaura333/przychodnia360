package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import pl.przychodnia360.AbstractIntegrationTest;

/**
 * Bramka fali 0R (ADR-M05): żaden klucz obcy nie przekracza granicy modułu (przyszłej usługi), a każda tabela jest
 * przypisana do modułu. Nowa tabela wymaga wpisu w {@link #MODULE_OF}, więc o granicy decyduje się świadomie.
 */
class ModuleBoundariesFkTest extends AbstractIntegrationTest {

    static final Map<String, String> MODULE_OF = Map.ofEntries(
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
            Map.entry("appointments", "scheduling"));

    /** Tabele pomocnicze, poza modułami. */
    static final Set<String> IGNORED = Set.of("flyway_schema_history", "_tenant_probe");

    @Autowired
    DataSource dataSource;

    @Test
    void everyTableBelongsToAModule() {
        List<String> tables = JdbcClient.create(dataSource).sql("""
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relkind = 'r'
                """).query(String.class).list();
        assertThat(tables).filteredOn(t -> !IGNORED.contains(t))
                .as("tabele bez przypisanego modułu (dopisz w MODULE_OF)")
                .allSatisfy(t -> assertThat(MODULE_OF).containsKey(t));
    }

    @Test
    void noForeignKeyCrossesModuleBoundary() {
        List<Map<String, Object>> fks = JdbcClient.create(dataSource).sql("""
                SELECT c.conrelid::regclass::text AS from_table, c.confrelid::regclass::text AS to_table,
                       c.conname AS name
                  FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE c.contype = 'f' AND n.nspname = 'public'
                """).query().listOfRows();
        assertThat(fks).as("test nie może być pusty: w modułach zostają FK wewnętrzne").isNotEmpty();
        for (Map<String, Object> fk : fks) {
            String from = (String) fk.get("from_table");
            String to = (String) fk.get("to_table");
            if (IGNORED.contains(from) || IGNORED.contains(to)) {
                continue;
            }
            assertThat(MODULE_OF.get(from)).as("FK %s: %s -> %s przekracza granicę modułu", fk.get("name"), from, to)
                    .isEqualTo(MODULE_OF.get(to));
        }
    }
}
