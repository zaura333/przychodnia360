# ADR-M06: Krawędź i routing po podścieżkach

Status: przyjęte (fala 0R, 2026-11-14; zlecenie właściciela „zrób 0R"). Implementacja reguł Traefik/ALB w fali 1.5.

## Kontekst

Frontend woła jedno API pod `/api/v1/...`. Po podziale na usługi krawędź (Traefik lokalnie, CloudFront → ALB w AWS) kieruje żądania do właściwej usługi. Inwentarz API (fala 0, B4) pokazał, że prefiksy `/admin/*`, `/me/*` i `/reception/*` łączą kilka usług, więc routing po pierwszym segmencie ścieżki nie działa.

## Decyzja

1. **Wariant (a): reguły na poziomie podścieżek.** Kontrakt API dla frontendu bez zmian; krawędź rozstrzyga po drugim i trzecim segmencie, np. `/admin/employees*` → scheduling, `/admin/services*` i `/admin/service-categories*` → catalog, `/me` → iam, `/me/patient` → patient, `/me/appointments` i `/me/schedule` → scheduling, `/reception/appointments` → scheduling, `/reception/patients*` → patient.
2. **Tabela routingu jako źródło prawdy:** `routing-table.csv` (wzorce ścieżek → usługa). Test `RoutingTableConsistencyTest` sprawdza, że każda ścieżka z `api/openapi.json` pasuje do dokładnie jednej reguły i że żadna reguła nie jest martwa. W fali 1.5 te same reguły generują konfigurację Traefik i ALB.
3. Nowy endpoint, który nie pasuje do żadnej reguły (albo pasuje do dwóch), zatrzymuje CI do czasu dopisania reguły.
4. Limity żądań i `OriginGuard` zostają w aplikacji; przeniesienie na krawędź jest osobną decyzją fali 1.5.

## Konsekwencje

- Reguły są dłuższe niż routing po prefiksie i muszą nadążać za API; test to pilnuje.
- `/health/**` jest „every": każda usługa wystawia własne `health`, a krawędź kieruje sprawdzenia kondycji do konkretnej usługi (osobna ścieżka w fali 1.5).
- Zmiana ścieżek w API (wariant b) pozostaje opcją, jeśli reguły podścieżek okażą się zbyt kruche.

## Odrzucone opcje

- **Wariant (b): zmiana ścieżek i regeneracja klienta Orval:** koszt po stronie frontendu bez korzyści funkcjonalnej.
- **Routing po pierwszym segmencie:** nie rozdziela `/admin`, `/me`, `/reception`.
