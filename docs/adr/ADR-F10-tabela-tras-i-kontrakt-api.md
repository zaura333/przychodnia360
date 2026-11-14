# ADR-F10: Tabela tras z rolami i kontrakt API z polami wymaganymi

Status: przyjęte (etap 1a frontendu, 2026-11-14)

## Kontekst

Dostęp do tras ma być opisany raz i sprawdzany testami, a wygenerowany klient ma dawać typy bez ciągłego sprawdzania `undefined`.

## Decyzja

**Tabela tras** `ROUTE_TABLE` (`app/routeTable.ts`): wzorzec trasy, przykładowy adres i role. `RoleGate` w obszarach pokazuje stronę `403` rolom spoza wiersza, a testy generują się z tej samej tabeli: "rola x trasa x wynik" (każdy wiersz, każda rola) i "rola x pozycja menu" (każda pozycja `NAVIGATION` musi prowadzić do trasy dozwolonej dla tej roli). Wejście do obszaru kontroluje `RequireRole`. Dodanie ekranu oznacza dopisanie wiersza do `ROUTE_TABLE`, a pozycji menu do `NAVIGATION`. To wyłącznie wygoda UI: dostęp do danych egzekwuje backend.

**Pola wymagane w OpenAPI:** springdoc oznacza pola odpowiedzi jako opcjonalne, co dawało typy `id?: string` w całym kliencie. Backend (`OpenApiConfig.responseFieldsRequired`) oznacza jako wymagane wszystkie pola schematów `*Response` i `*View`, z jawną listą pól pustych (np. `tenantId` w `MeResponse`). Zmiana kontraktu jest widoczna w `api/openapi.json` i wymusza kompilację ekranów.

**Klient:** ekrany wołają wygenerowane funkcje (nie hooki Orval) z własnymi hookami TanStack Query, których klucze budują się z `qk.user(userId)`. Wywołania `/auth/*` i `/me` pozostają ręczne (ADR-F03).

## Konsekwencje

Lista pól pustych w backendzie wymaga utrzymania razem z DTO (nowe pole puste bez wpisu zostanie opisane jako wymagane). Hooki są pisane ręcznie, ale zależności od kształtu odpowiedzi wykrywa `tsc`.

## Odrzucone opcje

- `@Schema(requiredMode = REQUIRED)` na każdym polu rekordów: dużo szumu w kodzie backendu.
- Hooki generowane przez Orval w ekranach: klucze zapytań bez `userId` i wrażliwy na zmiany nazw operacji interfejs.
