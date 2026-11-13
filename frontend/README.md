# Przychodnia360: frontend

Aplikacja jednostronicowa (React 19, TypeScript, Vite, Tailwind CSS 4) z obszarami ról ładowanymi leniwie. Wersja demonstracyjna, dane wyłącznie syntetyczne.

## Uruchomienie

Wymagania: Node z `.nvmrc` (22), npm. Backend (opcjonalnie) z `docker compose up -d --wait` i `./mvnw` w katalogu `backend`.

```bash
npm ci               # instaluje zależności i generuje klienta API (postinstall)
npm run dev          # http://localhost:5173, proxy /api -> http://localhost:8080 (VITE_DEV_BACKEND)
```

| Skrypt                   | Opis                                                                                    |
| ------------------------ | --------------------------------------------------------------------------------------- |
| `npm run typecheck`      | TypeScript `strict` (aplikacja i konfiguracja)                                          |
| `npm run lint`           | ESLint z regułami projektowymi                                                          |
| `npm run format:check`   | Prettier                                                                                |
| `npm test`               | Vitest (jsdom, `TZ=America/Los_Angeles`, MSW)                                           |
| `npm run build`          | typecheck + build produkcyjny z manifestem                                              |
| `npm run check:bundle`   | budżet rozmiaru i test manifestu (obszary w osobnych chunkach)                          |
| `npm run test:e2e`       | Playwright (Chromium) przeciw `vite preview` z nagłówkami CSP; `BASE_URL` pomija serwer |
| `npm run bench:schedule` | pomiary prototypu terminarza (Playwright, build produkcyjny z CSP, `dist-bench`)        |
| `npm run api:generate`   | klient API (Orval) z `../api/openapi.json`; wynik nie jest commitowany                  |

Pierwsze uruchomienie e2e: `npx playwright install chromium`.

## Struktura

```text
styles/globals.css         tokeny (jasny i ciemny motyw), Tailwind
security-headers.json      jedno źródło nagłówków bezpieczeństwa (preview, Nginx, CloudFront)
src/app/                   router, układy, strony, dostawcy, obszary ról (areas/<obszar>, osobne chunki)
src/features/<funkcja>/    funkcje; import z innej funkcji tylko przez index.ts (lint)
src/shared/api/            mutator (http.ts), ApiError, authClient, tokenStore, queryClient, wygenerowany klient
src/shared/lib/            datetime, format, mask, permissions, errors, limits
src/shared/ui/             komponenty bazowe, QueryState, ErrorBoundary, DemoBanner, Toaster
src/shared/test/           MSW, fabryki danych, renderWithProviders, pomocnik axe
e2e/                       testy Playwright (smoke, CSP)
```

## Zasady bezpieczeństwa

- **Token dostępu tylko w pamięci** (`tokenStore`), nigdy w `localStorage` ani `sessionStorage`. Sesję odtwarza ciasteczko `HttpOnly` przez `refresh`.
- **Brak trwałego cache zapytań** (bez persistera): dane z pamięci podręcznej nie trafiają na dysk. Cache jest czyszczony przy wylogowaniu, wygaśnięciu sesji i zmianie użytkownika.
- **Brak `dangerouslySetInnerHTML`**, brak `console.*` w `src/`, brak `any` i `@ts-ignore` bez opisu (lint).
- **Daty i czas wyłącznie przez `shared/lib/datetime`** (strefa Europe/Warsaw, typy `Instant` i `LocalDate`); `new Date`, `Date.now`, `Intl.DateTimeFormat` i `toLocale*String` poza tym modułem blokuje lint.
- **Komunikaty błędów tylko przez `describeError()`**; `error.detail` nie trafia do JSX (lint).
- **Uprawnienia w UI to wygoda**, nie zabezpieczenie: dostęp do danych egzekwuje backend.
- **CSP bez wyjątków** (`style-src 'self'`, `script-src 'self'`). Komponenty, które wstrzykują `<style>` w czasie działania, są wykluczone: `ScrollArea` i `Select` z Radix, `Dialog` z Radix (blokada przewijania), Sonner. Zamiast nich: natywne przewijanie (`overflow-auto`), natywny `<select>`, natywny `<dialog>`, własny `Toaster`, `DropdownMenu` z `modal={false}`.
- **Czcionki systemowe**, żadnych zasobów spoza domeny.

## Lista kontrolna: jak dodać ekran

1. Dane pobieraj hookami z wygenerowanego klienta i pokazuj **wyłącznie przez `QueryState`** (szkielet, stan pusty, błąd z "Spróbuj ponownie").
2. Klucze zapytań budować z `qk.user(userId).scoped(...)`.
3. Obsłuż `403`, `404`, `409`, `422` przez `describeError` (nowe typy Problem Details dopisz do słownika w `shared/lib/errors`).
4. Etykiety i komunikaty po polsku; pola formularzy przez `FormField` (etykieta, błąd, `aria-*`).
5. Dopisz wiersz do `NAVIGATION` i do tabeli tras; testy "rola x trasa x wynik" i "rola x pozycja menu" generują się z tych tabel.
6. Żadnych danych osobowych ani medycznych w URL (identyfikatory, nie PESEL).
7. Test dostępności: `expectNoA11yViolations(container)`.

## Klient API

Orval generuje hooki TanStack Query z `../api/openapi.json` do `src/shared/api/generated` (katalog w `.gitignore`). Mutator `customFetch` dodaje token i `X-Request-Id`, odświeża sesję jednym wspólnym wywołaniem przy równoległych `401`, rozróżnia limit czasu od anulowania i publikuje zdarzenie powolnej odpowiedzi (>5 s). Ręczne są tylko `/auth/*` i `/me` (`authClient.ts`), żeby uniknąć zależności cyklicznej.

## Architecture Decision Records

W `../docs/adr/` (prefiks `ADR-F`): stos i struktura, sesja i tokeny, klient API, czas i strefy, CSP i motyw, strategia testów. ADR-F04 opisuje wybór komponentu terminarza (własny `ScheduleGrid`); pomiary powtórzysz przez `npm run bench:schedule`.
