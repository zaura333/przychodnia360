# ADR-F03: Klient API z OpenAPI

Status: przyjęte (etap 0 frontendu, 2026-11-13)

## Kontekst

Kontrakt backendu jest zatwierdzany w repozytorium (`api/openapi.json`, generowany testem w `mvn verify`). Frontend ma z niego korzystać bez ręcznego duplikowania typów.

## Decyzja

Orval (`tags-split`, klient `react-query`, `httpClient: fetch`, własny mutator `customFetch`, mocki MSW) generuje kod do `src/shared/api/generated`, który nie jest commitowany; `postinstall` i `npm run api:generate` go odtwarzają (czytelny komunikat przy braku kontraktu). Wyjątek: `/auth/*` i `/me` są wywoływane ręcznie w `authClient.ts` z typami tymczasowymi, bo mutator sam z nich korzysta (zależność cykliczna z wygenerowanym klientem); w 1a typy ręczne zastępują typy generowane, a test sprawdza ich przypisywalność. Pliki `*.faker.ts` i `*.msw.ts` są wykluczone z `tsc`, bo generator nie spełnia `exactOptionalPropertyTypes`.

## Konsekwencje

Rozjazd kontraktu wychodzi w typach. Handlery bazowe MSW są ręczne do 1a. Schematy Zod z OpenAPI pominięte do czasu stabilnej wtyczki.

## Odrzucone opcje

Ręczne typy (rozjazd z backendem); commitowanie wygenerowanego kodu (szum w przeglądach).
