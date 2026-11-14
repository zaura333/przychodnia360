# ADR-F01: Stos i struktura aplikacji

Status: przyjęte (etap 0 frontendu, 2026-11-13)

## Kontekst

Aplikacja ma obsłużyć cztery grupy użytkowników (pacjent, personel, administrator placówki, administrator platformy) bez pobierania przez jedną grupę kodu pozostałych.

## Decyzja

Jedna SPA (React 19, TypeScript strict, Vite, Tailwind 4, React Router w trybie biblioteki) z obszarami ról ładowanymi leniwie (`lazy` na trasie, osobny chunk). Struktura według funkcji (`src/features/<funkcja>`), import z innej funkcji tylko przez `index.ts` (reguła `no-restricted-imports`), alias `@/` na `src/`. Reguły lintera od pierwszego dnia: brak `dangerouslySetInnerHTML`, `console`, `any`, bezpośredniego `Date`. Menedżer pakietów npm, skrypty wieloplatformowe.

## Konsekwencje

Zalety: mały chunk początkowy (145 kB gzip), mechaniczna egzekucja granic (`check:bundle` sprawdza manifest). Koszt: lint typowany spowalnia `npm run lint`. Wersje: TypeScript 6.0 i ESLint 9, bo `typescript-eslint` i wtyczki nie wspierają jeszcze TS 7 ani ESLint 10; jsdom 27, bo jsdom 31 wymaga Node 22.22+.

## Odrzucone opcje

Next.js lub Remix (renderowanie po stronie serwera niepotrzebne, dodatkowy runtime); wiele aplikacji (duplikacja shared); tryb frameworka React Router.
