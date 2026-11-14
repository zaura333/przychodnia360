# ADR-M06: Krawędź i routing po podścieżkach

Status: przyjęte (fala 0R, 2026-11-14; zlecenie właściciela „zrób 0R"); **zmienione 2026-11-14 (D-048, zadanie 1.5): Traefik także w AWS, bez ALB z regułami ścieżek**. Lokalnie zaimplementowane; część AWS dojdzie z Terraformem.

## Kontekst

Frontend woła jedno API pod `/api/v1/...`. Po podziale na usługi krawędź (Traefik; w AWS za CloudFront i wewnętrznym NLB) kieruje żądania do właściwej usługi. Inwentarz API (fala 0, B4) pokazał, że prefiksy `/admin/*`, `/me/*` i `/reception/*` łączą kilka usług, więc routing po pierwszym segmencie ścieżki nie działa.

## Decyzja

1. **Wariant (a): reguły na poziomie podścieżek.** Kontrakt API dla frontendu bez zmian; krawędź rozstrzyga po drugim i trzecim segmencie, np. `/admin/employees*` → scheduling, `/admin/services*` i `/admin/service-categories*` → catalog, `/me` → iam, `/me/patient` → patient, `/me/appointments` i `/me/schedule` → scheduling, `/reception/appointments` → scheduling, `/reception/patients*` → patient.
2. **Tabela routingu jako źródło prawdy:** `docker/edge/routing-table.csv` (wzorce ścieżek → usługa; adresy usług w `docker/edge/upstreams.csv`). Test `RoutingTableConsistencyTest` sprawdza, że każda ścieżka z `api/openapi.json` pasuje do dokładnie jednej reguły i że żadna reguła nie jest martwa. Z tych tabel powstaje konfiguracja Traefika (`docker/traefik/dynamic/routes.yml`, generowana i sprawdzana przez `EdgeRoutesTest`); to jedyna implementacja reguł, używana lokalnie i w AWS.
3. Nowy endpoint, który nie pasuje do żadnej reguły (albo pasuje do dwóch), zatrzymuje CI do czasu dopisania reguły.
4. **Traefik wszędzie (D-048).** Lokalnie jako usługa compose (profil `edge`), w AWS jako zadanie ECS Fargate. Reguły ścieżek są tylko w Traefiku; ALB z regułami ścieżek nie powstaje.
5. **Wejście w AWS:** CloudFront → VPC origin → **wewnętrzny NLB (warstwa 4, bez żadnych reguł routingu)** → Traefik (Fargate) → usługi. NLB jest potrzebny wyłącznie jako stabilny adres dla CloudFront, bo zadanie Fargate nie może być VPC origin bezpośrednio (do potwierdzenia przy Terraformie). Adresy usług w AWS pochodzą z Cloud Map (nazwy DNS), więc `upstreams.aws.csv` zamiast `upstreams.csv`; reguły (`routing-table.csv`) są te same.
6. Limity żądań i `OriginGuard` zostają w aplikacji; przeniesienie na krawędź jest osobną decyzją.

## Konsekwencje

- Reguły są dłuższe niż routing po prefiksie i muszą nadążać za API; test to pilnuje.
- `/health/**` jest „every": każda usługa wystawia własne `health`; dziś krawędź kieruje je do monolitu, po wycięciu usług sprawdzenia kondycji trzeba przenieść na osobne ścieżki per usługa.
- **Jedna implementacja reguł:** brak ryzyka rozjazdu semantyki ALB i Traefika (inne wzorce `*`, priorytety, limity reguł); to, co działa w e2e lokalnie, jest tym samym kodem krawędzi co w AWS.
- **Koszt:** Traefik jest dodatkowym zadaniem do utrzymania w AWS (kilka dziesiątek MB RAM, ale to jeden punkt awarii przy jednym zadaniu; do rozważenia dwa zadania) oraz NLB (stały koszt, w demo niszczony po pokazie razem ze środowiskiem). ALB zapewniał za darmo health-checki i wymianę zadań bez przerwy: przy Traefiku trzeba to sprawdzić przy pierwszym wdrożeniu (rolling deploy, TTL DNS w Cloud Map, biernik błędów upstreamu).
- Część AWS (moduł Terraform `edge`, `upstreams.aws.csv`, zadanie Traefik, NLB) nie istnieje: w repo nie ma jeszcze Terraformu.
- Zmiana ścieżek w API (wariant b) pozostaje opcją, jeśli reguły podścieżek okażą się zbyt kruche.

## Odrzucone opcje

- **Wariant (b): zmiana ścieżek i regeneracja klienta Orval:** koszt po stronie frontendu bez korzyści funkcjonalnej.
- **Routing po pierwszym segmencie:** nie rozdziela `/admin`, `/me`, `/reception`.
- **ALB z regułami ścieżek w AWS (plan z D16/D13) i Traefik lokalnie:** dwie implementacje tych samych reguł o różnej składni; ALB nie da się uruchomić lokalnie, więc rozjazd wychodziłby dopiero po wdrożeniu. Odrzucone na życzenie właściciela (D-048) na rzecz jednej implementacji.
- **API Gateway, Spring Cloud Gateway:** koszt lub dodatkowa JVM bez potrzeby agregacji odpowiedzi.
