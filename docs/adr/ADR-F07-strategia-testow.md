# ADR-F07: Strategia testów

Status: przyjęte (etap 0 frontendu, 2026-11-13)

## Kontekst

Potrzebne są szybkie testy logiki i uprawnień oraz dowód działania w przeglądarce, bez wychodzenia do sieci.

## Decyzja

Vitest (jsdom) z MSW 3 (`onUnhandledFrame: error`, handlery bazowe ręczne do 1a), `renderWithProviders` i `renderApp`, fabryki `testData`, axe (`expectNoA11yViolations`). Testy parametryczne generowane z tabel: "rola x trasa x wynik" (z `AREAS`) i "rola x pozycja menu" (z `NAVIGATION`), więc 1a dopisuje tylko wiersze. Test kontrastu tokenów. Budżet rozmiaru i test manifestu (`check:bundle`, z testami jednostkowymi reguł). Playwright (Chromium) przeciw `vite preview` z nagłówkami (smoke: ładowanie, przekierowanie, 404, CSP, nagłówki, błąd sieci przy odświeżaniu); od 1a e2e na prawdziwym backendzie.

## Konsekwencje

Zielone e2e na podglądzie nie dowodzi zachowania CloudFront ani Lambdy; to sprawdza smoke po wdrożeniu. Testy czasu działają w obcej strefie.

## Odrzucone opcje

Cypress (cięższy, drugi runner); testy bez MSW (zależność od backendu).
