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
 * Bramka dla wszystkich przyszłych migracji: każda tabela z kolumną tenant_id ma włączone i wymuszone RLS
 * oraz politykę, a tabele bez tenant_id są wyłącznie na jawnej liście wyjątków.
 */
class RlsCoverageTest extends AbstractIntegrationTest {

    /** Tabele bez tenant_id. Dopisanie tabeli tutaj wymaga uzasadnienia w przeglądzie. */
    private static final Set<String> TABLES_WITHOUT_TENANT_ID = Set.of(
            "tenants",               // słownik placówek, publiczna lista
            "flyway_schema_history"  // metadane migracji (niedostępne dla p360_app)
    );

    @Autowired
    DataSource dataSource;

    @Test
    void everyTenantTableHasForcedRlsAndPolicy() {
        List<Map<String, Object>> tables = JdbcClient.create(dataSource).sql("""
                SELECT c.relname AS name, c.relrowsecurity AS rls, c.relforcerowsecurity AS forced,
                       (SELECT count(*) FROM pg_policies p
                         WHERE p.schemaname = 'public' AND p.tablename = c.relname) AS policies
                  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relkind = 'r'
                   AND EXISTS (SELECT 1 FROM pg_attribute a
                                WHERE a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped)
                """).query().listOfRows();

        assertThat(tables).as("test nie może być pusty: tabela wzorcowa _tenant_probe istnieje")
                .extracting(r -> r.get("name")).contains("_tenant_probe");
        for (Map<String, Object> t : tables) {
            assertThat(t.get("rls")).as("RLS włączone w %s", t.get("name")).isEqualTo(true);
            assertThat(t.get("forced")).as("FORCE RLS w %s", t.get("name")).isEqualTo(true);
            assertThat(((Number) t.get("policies")).intValue()).as("polityki w %s", t.get("name")).isPositive();
        }
    }

    @Test
    void tablesWithoutTenantIdAreExplicitlyListed() {
        List<String> names = JdbcClient.create(dataSource).sql("""
                SELECT c.relname
                  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relkind = 'r'
                   AND NOT EXISTS (SELECT 1 FROM pg_attribute a
                                    WHERE a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped)
                """).query(String.class).list();

        assertThat(names).isNotEmpty().allSatisfy(n -> assertThat(TABLES_WITHOUT_TENANT_ID).contains(n));
    }

    /** Zakres platformy (app_scope) znają wyłącznie tabele tożsamości; żadna tabela danych placówki go nie zna. */
    @Test
    void onlyIdentityTablesKnowPlatformScope() {
        List<String> tables = JdbcClient.create(dataSource).sql(
                "SELECT DISTINCT tablename FROM pg_policies WHERE schemaname = 'public' "
                        + "AND (qual LIKE '%app_scope%' OR with_check LIKE '%app_scope%')").query(String.class).list();
        assertThat(tables).containsExactlyInAnyOrder("users", "refresh_tokens", "password_reset_tokens");
    }

    /** Dostęp przekaźnika (app_relay) do wierszy wszystkich placówek ma wyłącznie outbox. */
    @Test
    void onlyOutboxKnowsRelayScope() {
        List<String> tables = JdbcClient.create(dataSource).sql(
                "SELECT DISTINCT tablename FROM pg_policies WHERE schemaname = 'public' "
                        + "AND (qual LIKE '%app_relay%' OR with_check LIKE '%app_relay%')").query(String.class).list();
        assertThat(tables).containsExactly("outbox");
    }
}
