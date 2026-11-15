package pl.przychodnia360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

/**
 * Migracja V12 (usunięcie tabel katalogu i pacjentów po wydzieleniu usług) na osobnej bazie: tabela z danymi blokuje
 * migrację czytelnym błędem i zostaje nietknięta (polityki RLS bez zmian), a po opróżnieniu tabele znikają.
 */
class ExtractedModulesMigrationTest extends AbstractIntegrationTest {

    private static final String DB = "p360_mig_extracted";

    private Connection superuser(String db) throws SQLException {
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + db + "$1");
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DB + "$1"), OWNER_ROLE, OWNER_PASSWORD)
                .locations("classpath:db/migration")
                .placeholders(Map.of("app_password", APP_PASSWORD));
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private void asOwner(String tenant, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DB + "$1"),
                OWNER_ROLE, OWNER_PASSWORD); Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenant + "', false)");
            st.execute(sql);
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (Connection c = superuser(DB); Statement st = c.createStatement();
                var rs = st.executeQuery("SELECT to_regclass('public." + table + "') IS NOT NULL")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    @Test
    void tablesWithDataBlockTheMigrationAndEmptyTablesAreDropped() throws Exception {
        try (Connection c = superuser("postgres"); Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DB);
            st.execute("CREATE DATABASE " + DB + " OWNER " + OWNER_ROLE);
        }
        flyway("11").migrate();
        String tenant = UUID.randomUUID().toString();
        String patient = UUID.randomUUID().toString();
        asOwner(tenant, "INSERT INTO patients (id, tenant_id, first_name, last_name, birth_date) VALUES ('" + patient
                + "', '" + tenant + "', 'Anna', 'Nowak', '1990-01-01')");

        assertThatThrownBy(() -> flyway("12").migrate()).isInstanceOf(FlywayException.class)
                .hasMessageContaining("patients").hasMessageContaining("zawiera 1 wierszy")
                .hasMessageContaining("copy-extracted-data.sh");
        assertThat(tableExists("patients")).as("tabela z danymi zostaje").isTrue();
        try (Connection c = superuser(DB); Statement st = c.createStatement();
                var rs = st.executeQuery("SELECT relforcerowsecurity FROM pg_class WHERE relname = 'patients'")) {
            rs.next();
            assertThat(rs.getBoolean(1)).as("FORCE RLS przywrócone po wycofaniu migracji").isTrue();
        }

        asOwner(tenant, "DELETE FROM patients");
        flyway("12").migrate();

        assertThat(tableExists("patients")).isFalse();
        assertThat(tableExists("services")).isFalse();
        assertThat(tableExists("service_categories")).isFalse();
        assertThat(tableExists("outbox")).as("tabele platformowe zostają").isTrue();
        assertThat(tableExists("appointments")).as("tabele scheduling zostają").isTrue();
    }

    /** V13 (S3): konta i placówki należą do iam; tabela z danymi blokuje migrację, puste tabele i funkcje znikają. */
    @Test
    void identityTablesWithDataBlockV13AndEmptyOnesAreDropped() throws Exception {
        try (Connection c = superuser("postgres"); Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + DB);
            st.execute("CREATE DATABASE " + DB + " OWNER " + OWNER_ROLE);
        }
        flyway("12").migrate();
        String tenant = UUID.randomUUID().toString();
        asOwner(tenant, "INSERT INTO tenants (id, slug, name) VALUES ('" + tenant + "', 'klinika-x', 'X')");

        assertThatThrownBy(() -> flyway(null).migrate()).isInstanceOf(FlywayException.class)
                .hasMessageContaining("tenants").hasMessageContaining("iam").hasMessageContaining("copy-extracted-data.sh");
        assertThat(tableExists("tenants")).as("tabela z danymi zostaje").isTrue();

        asOwner(tenant, "DELETE FROM tenants");
        flyway(null).migrate();

        for (String table : new String[] {"tenants", "users", "refresh_tokens", "password_reset_tokens"}) {
            assertThat(tableExists(table)).as(table).isFalse();
        }
        assertThat(tableExists("employees")).as("tabele scheduling zostają").isTrue();
        assertThat(tableExists("outbox")).as("tabele platformowe zostają").isTrue();
    }
}
