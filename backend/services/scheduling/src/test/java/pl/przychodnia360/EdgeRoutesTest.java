package pl.przychodnia360;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fala 1.5: zatwierdzony docker/traefik/dynamic/routes.yml jest dokładnie tym, co wynika z tabel routingu.
 * Po zmianie docker/edge/*.csv uruchom test z {@code -Dp360.edge.update=true}, żeby przepisać plik.
 */
class EdgeRoutesTest {

    private static final String UPDATE_PROPERTY = "p360.edge.update";

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("docker"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Nie znaleziono korzenia repozytorium");
        }
        return dir;
    }

    @Test
    void committedTraefikRoutesMatchTheRoutingTables() throws IOException {
        Path root = repoRoot();
        String routing = Files.readString(root.resolve("docker/edge/routing-table.csv"), StandardCharsets.UTF_8);
        String upstreams = Files.readString(root.resolve("docker/edge/upstreams.csv"), StandardCharsets.UTF_8);
        String generated = EdgeRoutes.generate(routing, upstreams);
        Path target = root.resolve("docker/traefik/dynamic/routes.yml");

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.createDirectories(target.getParent());
            Files.writeString(target, generated, StandardCharsets.UTF_8);
        }

        assertThat(Files.readString(target, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("routes.yml jest nieaktualny: uruchom test z -D%s=true", UPDATE_PROPERTY)
                .isEqualTo(generated);
    }

    @Test
    void everyRuleHasAnUpstreamAndRouterNamesAreUnique() throws IOException {
        Path root = repoRoot();
        List<EdgeRoutes.Rule> rules = EdgeRoutes.rules(
                Files.readString(root.resolve("docker/edge/routing-table.csv"), StandardCharsets.UTF_8));
        var upstreams = EdgeRoutes.upstreams(
                Files.readString(root.resolve("docker/edge/upstreams.csv"), StandardCharsets.UTF_8));

        assertThat(rules).isNotEmpty();
        assertThat(rules).extracting(EdgeRoutes.Rule::service).allSatisfy(s -> assertThat(upstreams).containsKey(s));
        assertThat(rules).extracting(EdgeRoutes.Rule::routerName).doesNotHaveDuplicates();
    }

    @Test
    void exactAndPrefixRulesProduceTheExpectedTraefikRules() {
        assertThat(new EdgeRoutes.Rule("/api/v1/me", "iam").traefikRule()).isEqualTo("Path(`/api/v1/me`)");
        assertThat(new EdgeRoutes.Rule("/api/v1/auth/**", "iam").traefikRule())
                .isEqualTo("Path(`/api/v1/auth`) || PathPrefix(`/api/v1/auth/`)");
        assertThat(new EdgeRoutes.Rule("/api/v1/auth/**", "iam").routerName()).isEqualTo("auth-prefix");
        assertThat(new EdgeRoutes.Rule("/api/v1/me/patient", "patient").routerName()).isEqualTo("me-patient");
    }
}
