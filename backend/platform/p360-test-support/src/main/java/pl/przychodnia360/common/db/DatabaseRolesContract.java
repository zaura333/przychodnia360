package pl.przychodnia360.common.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import pl.przychodnia360.testsupport.PostgresIntegrationTest;

/** Aplikacja łączy się jako p360_app: nie superuser, bez BYPASSRLS, nie właściciel tabel, bez DDL. */
public abstract class DatabaseRolesContract extends PostgresIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void applicationConnectsAsRestrictedRole() {
        JdbcClient jdbc = JdbcClient.create(dataSource);

        assertThat(jdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo("p360_app");
        var flags = jdbc.sql("SELECT rolsuper, rolbypassrls, rolcreaterole, rolcreatedb FROM pg_roles WHERE rolname = current_user")
                .query().singleRow();
        assertThat(flags.values()).containsOnly(false);
    }

    @Test
    void applicationOwnsNoTables() {
        int owned = JdbcClient.create(dataSource)
                .sql("SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tableowner = current_user")
                .query(Integer.class).single();
        assertThat(owned).isZero();
    }

    @Test
    void applicationCannotCreateObjects() {
        assertThatThrownBy(() -> JdbcClient.create(dataSource).sql("CREATE TABLE hacked (id int)").update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void applicationCannotReadFlywayHistory() {
        assertThatThrownBy(() -> JdbcClient.create(dataSource).sql("SELECT * FROM flyway_schema_history").query().listOfRows())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void ownerRoleIsNotSuperuserAndDoesNotBypassRls() {
        var flags = JdbcClient.create(dataSource)
                .sql("SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'p360_owner'")
                .query().singleRow();
        assertThat(flags.values()).containsOnly(false);
    }

    @Test
    void resolveTenantReturnsEmptyForUnknownSlug() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        assertThat(jdbc.sql("SELECT resolve_tenant('brak-takiej')").query(java.util.UUID.class).optional()).isEmpty();
    }
}
