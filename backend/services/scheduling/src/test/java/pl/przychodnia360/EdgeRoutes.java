package pl.przychodnia360;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generator konfiguracji Traefika z tabel routingu ({@code docker/edge/routing-table.csv} i
 * {@code upstreams.csv}). Wynik jest deterministyczny, żeby test mógł porównać go z zatwierdzonym plikiem.
 */
final class EdgeRoutes {

    private EdgeRoutes() {
    }

    record Rule(String pattern, String service) {

        boolean prefix() {
            return pattern.endsWith("/**");
        }

        /** Ścieżka bazowa bez końcówki wzorca. */
        String base() {
            return prefix() ? pattern.substring(0, pattern.length() - 3) : pattern;
        }

        String routerName() {
            String name = base().replaceFirst("^/api/v1/?", "").replaceAll("[^a-zA-Z0-9]+", "-");
            name = name.replaceAll("^-|-$", "");
            return (name.isEmpty() ? "root" : name) + (prefix() ? "-prefix" : "");
        }

        /** Dokładna ścieżka albo ścieżka z podścieżkami; Traefik PathPrefix to prefiks tekstowy, więc z ukośnikiem. */
        String traefikRule() {
            return prefix()
                    ? "Path(`" + base() + "`) || PathPrefix(`" + base() + "/`)"
                    : "Path(`" + base() + "`)";
        }

        int priority() {
            return prefix() ? 100 : 200;
        }
    }

    static List<Rule> rules(String routingCsv) {
        List<Rule> rules = new ArrayList<>();
        String[] lines = routingCsv.split("\\R");
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                String[] cells = lines[i].split(",");
                rules.add(new Rule(cells[0].trim(), cells[1].trim()));
            }
        }
        return rules;
    }

    static Map<String, String> upstreams(String upstreamsCsv) {
        Map<String, String> upstreams = new TreeMap<>();
        String[] lines = upstreamsCsv.split("\\R");
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                String[] cells = lines[i].split(",");
                upstreams.put(cells[0].trim(), cells[1].trim());
            }
        }
        return upstreams;
    }

    static String generate(String routingCsv, String upstreamsCsv) {
        List<Rule> rules = rules(routingCsv);
        Map<String, String> upstreams = upstreams(upstreamsCsv);
        Map<String, Rule> byName = new LinkedHashMap<>();
        for (Rule rule : rules) {
            if (!upstreams.containsKey(rule.service())) {
                throw new IllegalStateException("Usługa " + rule.service() + " nie ma wpisu w upstreams.csv");
            }
            Rule duplicate = byName.put(rule.routerName(), rule);
            if (duplicate != null) {
                throw new IllegalStateException("Powtórzona nazwa routera " + rule.routerName());
            }
        }
        StringBuilder yaml = new StringBuilder();
        yaml.append("# PLIK WYGENEROWANY z docker/edge/routing-table.csv i upstreams.csv (EdgeRoutesTest). Nie edytuj ręcznie.\n");
        yaml.append("http:\n  routers:\n");
        for (Rule rule : rules) {
            yaml.append("    ").append(rule.routerName()).append(":\n");
            yaml.append("      entryPoints: [web]\n");
            yaml.append("      rule: \"").append(rule.traefikRule()).append("\"\n");
            yaml.append("      priority: ").append(rule.priority()).append("\n");
            yaml.append("      service: ").append(rule.service()).append("\n");
        }
        yaml.append("  services:\n");
        for (Map.Entry<String, String> upstream : upstreams.entrySet()) {
            yaml.append("    ").append(upstream.getKey()).append(":\n");
            yaml.append("      loadBalancer:\n        servers:\n          - url: \"")
                    .append(upstream.getValue()).append("\"\n");
        }
        return yaml.toString();
    }
}
