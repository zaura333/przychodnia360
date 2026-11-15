package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import pl.przychodnia360.AbstractIntegrationTest;

/**
 * Bramka retencji (09, sekcja 7): każda tabela ma klasę retencji (RC-n) albo jawny status słownika (DICT).
 * Nowa tabela bez wpisu w retention-classes.csv nie przechodzi.
 */
class RetentionClassCoverageTest extends AbstractIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void everyTableHasARetentionClass() throws IOException {
        Map<String, String> classes = new HashMap<>();
        try (var in = getClass().getResourceAsStream("/retention-classes.csv")) {
            assertThat(in).as("retention-classes.csv").isNotNull();
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
        assertThat(tables).filteredOn(t -> !ModuleBoundariesFkTest.IGNORED.contains(t))
                .as("tabele bez klasy retencji (dopisz w retention-classes.csv)")
                .allSatisfy(t -> assertThat(classes).containsKey(t));
        assertThat(classes.values()).allSatisfy(c -> assertThat(c).matches("DICT|RC-\\d+(\\|RC-\\d+)*"));
    }
}
