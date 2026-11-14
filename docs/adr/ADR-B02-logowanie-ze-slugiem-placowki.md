# ADR-B02: Logowanie ze slugiem placówki

Status: przyjęte (etap 0 backendu, 2026-11-14)

## Kontekst

Dane wielu placówek są w jednej bazie, izolowane przez RLS po `tenant_id`. Żądanie logowania nie niesie jeszcze kontekstu placówki, a ten kontekst jest potrzebny, żeby w ogóle odczytać użytkownika (RLS bez kontekstu zwraca zero wierszy). Ten sam adres e-mail może należeć do osób w różnych placówkach.

## Decyzja

- Logowanie przyjmuje `facilitySlug`, e-mail i hasło. E-mail jest unikalny w obrębie placówki (`UNIQUE (tenant_id, lower(email))`), nie globalnie.
- Slug zamienia na identyfikator placówki funkcja `resolve_tenant(slug)` (migracja V3): `SECURITY DEFINER`, `STABLE`, stały `search_path`, `EXECUTE` tylko dla `p360_app`, zwraca tylko aktywne placówki. Dzięki temu aplikacja nie potrzebuje `BYPASSRLS` ani szerokiego odczytu tabeli `tenants`.
- Po rozwiązaniu sluga transakcja logowania otwiera `TenantContext` placówki i dopiero w nim czyta użytkownika. Identyfikator placówki trafia do tokenu, a każde kolejne żądanie ustawia kontekst z tokenu.
- Administrator platformy loguje się osobnym endpointem (bez sluga), poza kontekstem placówki (`TenantContext.openWithoutTenant`), i ma dostęp tylko do danych platformy (placówki), nie do danych pacjentów.
- Odpowiedź na błędny slug, błędny e-mail i błędne hasło jest identyczna (ten sam status, treść i zbliżony czas), żeby nie ujawniać, które placówki i konta istnieją.

## Konsekwencje

- Frontend pyta o placówkę na ekranie logowania (lub bierze slug z adresu).
- Test: nieistniejący i nieaktywny slug dają ten sam wynik co złe hasło; użytkownik placówki A nie zaloguje się ze slugiem B.
- Funkcje `SECURITY DEFINER` są wyjątkiem od RLS, więc każda kolejna wymaga uzasadnienia w przeglądzie (lista kontrolna w README).

## Odrzucone opcje

- E-mail globalnie unikalny: blokuje osoby z kontami w kilku placówkach i ujawnia istnienie konta.
- Wyszukiwanie użytkownika po e-mailu bez kontekstu (rola z `BYPASSRLS`): jeden błąd wycieka dane między placówkami.
- Slug w subdomenie: wymaga dzikiej domeny i certyfikatu, zbędne w demo.
