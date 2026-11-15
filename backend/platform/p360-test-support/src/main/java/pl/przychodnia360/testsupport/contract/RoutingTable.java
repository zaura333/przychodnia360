package pl.przychodnia360.testsupport.contract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Tabela routingu krawędzi (docker/edge/routing-table.csv): wzorzec ścieżki -> usługa logiczna. Służy do
 * wyprowadzenia kontraktów OpenAPI per usługa. Usługa {@code every} (np. health) należy do każdej usługi.
 */
public final class RoutingTable {

    public static final String EVERY = "every";

    private record Rule(PathPattern pattern, String service) {
    }

    private final List<Rule> rules = new ArrayList<>();

    private RoutingTable() {
    }

    public static RoutingTable load() {
        try {
            String csv = Files.readString(EventSchemas.repoRoot().resolve("docker/edge/routing-table.csv"),
                    StandardCharsets.UTF_8);
            return parse(csv);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static RoutingTable parse(String csv) {
        RoutingTable table = new RoutingTable();
        String[] lines = csv.split("\\R");
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                String[] cells = lines[i].split(",");
                table.rules.add(new Rule(PathPatternParser.defaultInstance.parse(cells[0].trim()), cells[1].trim()));
            }
        }
        return table;
    }

    /** Usługi z tabeli (bez {@code every}), posortowane. */
    public List<String> services() {
        TreeSet<String> services = new TreeSet<>();
        rules.forEach(r -> services.add(r.service()));
        services.remove(EVERY);
        return new ArrayList<>(services);
    }

    /** Usługa obsługująca ścieżkę; wyjątek, gdy ścieżka nie pasuje do dokładnie jednej reguły. */
    public String serviceFor(String path) {
        List<Rule> matching = rules.stream()
                .filter(r -> r.pattern().matches(PathContainer.parsePath(path))).toList();
        if (matching.size() != 1) {
            throw new IllegalStateException("Ścieżka " + path + " pasuje do " + matching.size() + " reguł routingu");
        }
        return matching.get(0).service();
    }
}
