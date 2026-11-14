package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import pl.przychodnia360.testsupport.PostgresIntegrationTest;

/**
 * Bramka (ADR-M05): żaden klucz obcy nie przekracza granicy modułu (przyszłej usługi), a każda tabela jest
 * przypisana do modułu ({@link #moduleOf()}). Nowa tabela wymaga wpisu, więc o granicy decyduje się świadomie.
 */
public abstract class ModuleBoundariesFkContract extends PostgresIntegrationTest {

    @Autowired
    protected DataSource dataSource;

    /** Tabela -> moduł. Dostarcza usługa. */
    protected abstract Map<String, String> moduleOf();

    /** Tabele pomocnicze, poza modułami. */
    protected Set<String> ignoredTables() {
        return Set.of("flyway_schema_history", "_tenant_probe");
    }

    @Test
    void everyTableBelongsToAModule() {
        List<String> tables = JdbcClient.create(dataSource).sql("""
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relkind = 'r'
                """).query(String.class).list();
        assertThat(tables).filteredOn(t -> !ignoredTables().contains(t))
                .as("tabele bez przypisanego modułu (dopisz w moduleOf)")
                .allSatisfy(t -> assertThat(moduleOf()).containsKey(t));
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
            if (ignoredTables().contains(from) || ignoredTables().contains(to)) {
                continue;
            }
            assertThat(moduleOf().get(from)).as("FK %s: %s -> %s przekracza granicę modułu", fk.get("name"), from, to)
                    .isEqualTo(moduleOf().get(to));
        }
    }
}
