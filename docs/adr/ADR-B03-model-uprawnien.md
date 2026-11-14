# ADR-B03: Model uprawnień

Status: przyjęte (etap 0 backendu, 2026-11-14; część `user_permissions` wdrażana w 1c)

## Kontekst

Role (pacjent, rejestrator, lekarz, personel, administrator placówki, administrator platformy) pokrywają większość reguł, ale zdarzają się wyjątki per osoba (np. rejestrator z dostępem do statystyk, FR-37). Uprawnienia rozproszone po kodzie trudno przeglądać i testować.

## Decyzja

- **Role stałe** są podstawą dostępu, wpisane w token.
- **Uprawnienia dodatkowe** (`user_permissions`, migracja V16 w 1c) rozszerzają rolę o pojedyncze zdolności, np. `STATS_READ`. Wartości są tekstem w bazie, a dozwolony zestaw pilnuje enum w aplikacji i test spójności z danymi.
- **Jedna klasa `PermissionPolicy`** jest jedynym miejscem, które odpowiada na pytanie "czy rola i uprawnienia pozwalają na akcję". Kontrolery używają `@PreAuthorize` odwołującego się do niej, a nie własnych warunków.
- Domyślnie odmowa: endpoint bez `@PreAuthorize` i bez `@PublicEndpoint` jest odrzucany (już egzekwowane przez `PermissionMatrixTest`).
- Uprawnienia są czytane z bazy po ustawieniu kontekstu placówki, więc cofnięcie uprawnienia działa od następnego żądania, bez czekania na wygaśnięcie tokenu.
- Własność zasobu (pacjent widzi własne wizyty) to osobna reguła domenowa, nie uprawnienie: cudzy zasób daje `404`.

## Konsekwencje

- Macierz rola x endpoint jest testem (`PermissionMatrixTest`) i rośnie z każdym endpointem; zmiana polityki bez zmiany macierzy wywala build.
- Dodatkowy odczyt uprawnień na żądanie wymaga indeksu `(tenant_id, user_id)` i ewentualnie cache'u (1c).

## Odrzucone opcje

- Same role bez wyjątków: wymusza nowe role przy każdym wyjątku.
- Uprawnienia wyłącznie w tokenie: cofnięcie działa dopiero po jego wygaśnięciu.
- Zewnętrzny silnik polityk (np. OPA): zbyt ciężki dla zakresu demo.
