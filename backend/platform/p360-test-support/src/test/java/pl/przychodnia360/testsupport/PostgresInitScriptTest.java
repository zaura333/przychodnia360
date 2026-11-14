package pl.przychodnia360.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import pl.przychodnia360.testsupport.contract.EventSchemas;

/**
 * Zadanie 1.10: skrypt docker/postgres/init/01-roles.sh tworzy bazę główną i, z listy P360_SERVICE_DATABASES, bazę
 * oraz właściciela per usługa; właściciel jednej usługi nie wchodzi do bazy innej. Test używa tego samego skryptu co
 * compose i testy integracyjne.
 */
class PostgresInitScriptTest {

    /** Kontener, którego inicjalizacja ma się nie udać, nie osiągnie gotowości: nie czekamy domyślnych 60 s. */
    private static final Duration FAILING_START_TIMEOUT = Duration.ofSeconds(25);

    private static PostgreSQLContainer container(String serviceDatabases, String... env) {
        PostgreSQLContainer pg = new PostgreSQLContainer(PostgresIntegrationTest.POSTGRES_IMAGE)
                .withEnv("P360_DB_NAME", "p360")
                .withEnv("P360_OWNER_PASSWORD", "main-pass")
                .withEnv("P360_SERVICE_DATABASES", serviceDatabases)
                .withCopyFileToContainer(MountableFile.forHostPath(
                        EventSchemas.repoRoot().resolve("docker/postgres/init/01-roles.sh"), 0755),
                        "/docker-entrypoint-initdb.d/01-roles.sh")
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));
        for (int i = 0; i + 1 < env.length; i += 2) {
            pg.withEnv(env[i], env[i + 1]);
        }
        return pg;
    }

    private static Connection connect(PostgreSQLContainer pg, String database, String user, String password)
            throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + pg.getHost() + ":" + pg.getMappedPort(5432) + "/"
                + database, user, password);
    }

    private static List<String> column(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (var statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    @Test
    void createsTheMainDatabaseAndOneDatabasePerServiceWithIsolatedOwners() throws Exception {
        try (PostgreSQLContainer pg = container("notification,integration-hub",
                "P360_NOTIFICATION_OWNER_PASSWORD", "n-pass", "P360_INTEGRATION_HUB_OWNER_PASSWORD", "h-pass")) {
            pg.start();
            try (Connection admin = connect(pg, pg.getDatabaseName(), pg.getUsername(), pg.getPassword())) {
                assertThat(column(admin, "SELECT datname FROM pg_database"))
                        .contains("p360", "p360_notification", "p360_integration_hub");
                assertThat(column(admin, """
                        SELECT d.datname || '=' || pg_get_userbyid(d.datdba) FROM pg_database d
                         WHERE d.datname LIKE 'p360%' ORDER BY 1"""))
                        .containsExactly("p360=p360_owner", "p360_integration_hub=p360_integration_hub_owner",
                                "p360_notification=p360_notification_owner");
                assertThat(column(admin, """
                        SELECT rolname || ':' || rolsuper || ':' || rolcreaterole || ':' || rolbypassrls
                          FROM pg_roles WHERE rolname LIKE 'p360%' ORDER BY 1"""))
                        .as("właściciele: bez superużytkownika i BYPASSRLS, z CREATEROLE (V1 usługi tworzy rolę aplikacji)")
                        .containsExactly("p360_integration_hub_owner:false:true:false",
                                "p360_notification_owner:false:true:false", "p360_owner:false:true:false");
            }

            try (Connection own = connect(pg, "p360_notification", "p360_notification_owner", "n-pass")) {
                assertThat(column(own, "SELECT current_database()")).containsExactly("p360_notification");
            }
            assertThatThrownBy(() -> connect(pg, "p360_integration_hub", "p360_notification_owner", "n-pass"))
                    .as("właściciel jednej usługi nie wchodzi do bazy innej usługi")
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> connect(pg, "p360", "p360_notification_owner", "n-pass"))
                    .as("ani do bazy głównej").isInstanceOf(SQLException.class);
        }
    }

    @Test
    void withoutTheServiceListOnlyTheMainDatabaseIsCreated() throws Exception {
        try (PostgreSQLContainer pg = container("")) {
            pg.start();
            try (Connection admin = connect(pg, pg.getDatabaseName(), pg.getUsername(), pg.getPassword())) {
                assertThat(column(admin, "SELECT datname FROM pg_database WHERE datname LIKE 'p360%'"))
                        .containsExactly("p360");
            }
        }
    }

    @Test
    void aMissingOwnerPasswordStopsTheInitialisationInsteadOfCreatingAnOpenDatabase() {
        try (PostgreSQLContainer pg = container("notification").withStartupTimeout(FAILING_START_TIMEOUT)) {
            assertThatThrownBy(pg::start).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void anInvalidServiceNameStopsTheInitialisation() {
        try (PostgreSQLContainer pg = container("Bad_Name", "P360_BAD_NAME_OWNER_PASSWORD", "x")
                .withStartupTimeout(FAILING_START_TIMEOUT)) {
            assertThatThrownBy(pg::start).isInstanceOf(RuntimeException.class);
        }
    }
}
