package pl.przychodnia360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Jedno źródło prawdy dla wersji PostgreSQL: docker-compose.yml, Testcontainers i ADR muszą się zgadzać
 * (rozjazd wersji lokalnej i testowej wychodzi w CI, nie na produkcji). Test nie startuje Springa ani Dockera.
 */
class ComposeConsistencyTest {

    private static final Pattern COMPOSE_POSTGRES = Pattern.compile("(?m)^\\s+image:\\s*postgres:([\\w.\\-]+)\\s*$");
    private static final Pattern ADR_VERSION = Pattern.compile("(?m)^Wersja PostgreSQL:\\s*([\\w.\\-]+)\\s*$");

    static String composeVersion(String composeYaml) {
        return extract(COMPOSE_POSTGRES, composeYaml, "obrazu postgres w docker-compose.yml");
    }

    static String adrVersion(String adrMarkdown) {
        return extract(ADR_VERSION, adrMarkdown, "linii 'Wersja PostgreSQL:' w ADR");
    }

    private static String extract(Pattern pattern, String text, String what) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            throw new AssertionError("Nie znaleziono " + what);
        }
        return m.group(1);
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("docker-compose.yml"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Nie znaleziono korzenia repozytorium (docker-compose.yml)");
        }
        return dir;
    }

    @Test
    void postgresVersionIsTheSameInComposeTestsAndAdr() throws IOException {
        Path root = repoRoot();
        String compose = composeVersion(Files.readString(root.resolve("docker-compose.yml")));
        String adr = adrVersion(Files.readString(root.resolve("docs/adr/ADR-B01-postgresql-wersja-i-role.md")));
        String tests = AbstractIntegrationTest.POSTGRES_IMAGE.substring("postgres:".length());

        assertThat(compose).as("wersja w docker-compose.yml").isEqualTo(tests);
        assertThat(adr).as("wersja w ADR-B01").isEqualTo(tests);
    }

    @Test
    void shellScriptsUseLfLineEndingsAndAreCoveredByGitattributes() throws IOException {
        Path root = repoRoot();
        String script = Files.readString(root.resolve("docker/postgres/init/01-roles.sh"));
        assertThat(script).as("CRLF psuje start kontenera").doesNotContain("\r");
        assertThat(Files.readString(root.resolve(".gitattributes"))).contains("docker/** text eol=lf");
    }

    @Test
    void parsersDetectAVersionChangeInOneSource() {
        String composeOld = "services:\n  postgres:\n    image: postgres:16.6\n";
        String composeNew = "services:\n  postgres:\n    image: postgres:17.2\n";
        assertThat(composeVersion(composeOld)).isEqualTo("16.6");
        assertThat(composeVersion(composeNew)).isNotEqualTo(composeVersion(composeOld));
        assertThat(adrVersion("Status: x\n\nWersja PostgreSQL: 16.6\n")).isEqualTo("16.6");
    }

    @Test
    void parsersFailLoudlyWhenTheVersionCannotBeFound() {
        assertThatThrownBy(() -> composeVersion("services: {}")).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> adrVersion("# brak linii")).isInstanceOf(AssertionError.class);
    }
}
