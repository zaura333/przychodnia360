# Krawędź (edge): routing po podścieżkach

Frontend woła jedno API pod `/api/v1/...`, a krawędź kieruje żądania do właściwej usługi (ADR-M06).

- `routing-table.csv`: **źródło prawdy** (wzorzec ścieżki → usługa logiczna). Wzorzec `/x/**` obejmuje `/x` i wszystko pod nim; wzorzec bez `**` to dokładna ścieżka. Z tej tabeli powstaje konfiguracja Traefika (lokalnie) i, później, reguły ALB (AWS).
- `upstreams.csv`: adres każdej usługi logicznej w sieci compose. Dziś `catalog` i `patient` wskazują własne usługi (S1, S2), a pozostałe monolit `backend` (`clinic-core`); po wycięciu kolejnej usługi zmienia się tylko jej wiersz. W AWS (Traefik jako zadanie Fargate za wewnętrznym NLB, D-048) adresy będą nazwami z Cloud Map w osobnym `upstreams.aws.csv`; reguły (`routing-table.csv`) są wspólne. Tego pliku i modułu Terraform jeszcze nie ma.
- `../traefik/dynamic/routes.yml`: **plik wygenerowany**, nie edytuj ręcznie. Test `EdgeRoutesTest` sprawdza, że jest zgodny z tabelami, a `RoutingTableConsistencyTest`, że każda ścieżka z agregatu `api/openapi.json` (składanego z eksportów usług, ADR-M16) ma dokładnie jedną regułę.

Regeneracja po zmianie tabel (z katalogu `backend`, Bash):

    ./mvnw -pl services/clinic-core -am test -Dtest=EdgeRoutesTest -Dsurefire.failIfNoSpecifiedTests=false -Dp360.edge.update=true

Uruchomienie lokalnie (krawędź na `http://127.0.0.1:8090`, port zmienia `EDGE_PORT`):

    docker compose --profile core up -d --build --wait

Frontend przez krawędź: `VITE_DEV_BACKEND=http://127.0.0.1:8090 npm run dev` (lub `npm run test:e2e:real`).
