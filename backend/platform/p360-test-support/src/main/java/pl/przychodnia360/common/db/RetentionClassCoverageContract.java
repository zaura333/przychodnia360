package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import pl.przychodnia360.testsupport.PostgresIntegrationTest;

/**
 * Bramka retencji (09, sekcja 7): każda tabela usługi ma klasę retencji (RC-n) albo jawny status słownika (DICT)
 * w {@code retention-classes.csv} (zasób testowy usługi: table,class,note). Nowa tabela bez wpisu nie przechodzi.
 */
public abstract class RetentionClassCoverageContract extends PostgresIntegrationTest {

    @Autowired
    protected DataSource dataSource;

    /** Tabele pomocnicze, poza klasami retencji. */
    protected Set<String> ignoredTables() {
        return Set.of("flyway_schema_history", "_tenant_probe");
    }

    @Test
    void everyTableHasARetentionClass() throws IOException {
        Map<String, String> classes = new HashMap<>();
        try (var in = getClass().getResourceAsStream("/retention-classes.csv")) {
            assertThat(in).as("retention-classes.csv w zasobach testowych usługi").isNotNull();
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isBlank()) {
                    continue;
                }
                String[] cells = lines[i].split(",", 3);
                classes.put(cells[0], cells[1]);
            }
        }
        List<String> tables = JdbcClient.create(dataSource).sql("""
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relkind = 'r'
                """).query(String.class).list();
        assertThat(tables).filteredOn(t -> !ignoredTables().contains(t))
                .as("tabele bez klasy retencji (dopisz w retention-classes.csv)")
                .allSatisfy(t -> assertThat(classes).containsKey(t));
        assertThat(classes.values()).allSatisfy(c -> assertThat(c).matches("DICT|RC-\\d+(\\|RC-\\d+)*"));
    }
}
