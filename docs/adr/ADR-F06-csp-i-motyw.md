# ADR-F06: CSP i motyw

Status: przyjęte (etap 0 frontendu, 2026-11-13)

## Kontekst

Dane medyczne i ochrona przed XSS wymagają ścisłej polityki treści bez wyjątków; motyw ciemny nie może wymagać skryptów inline.

## Decyzja

CSP: `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`. Źródło prawdy: `frontend/security-headers.json` (używa go `vite preview`, później Nginx i Terraform/CloudFront). Skrypt motywu w osobnym pliku `public/theme-init.js`, ładowany synchronicznie z `<head>`; klucz `p360_theme` w `localStorage` (preferencja nie jest daną wrażliwą). Tokeny jako zmienne CSS w formacie heksadecymalnym (jasny `:root`, ciemny `.dark`), test kontrastu AA parsuje `globals.css`. Czcionki systemowe. Zod z `jitless: true`, bo JIT (`new Function`) zgłasza naruszenie CSP. Wykluczone komponenty wstrzykujące style (Radix `ScrollArea`, `Select`, `Dialog`, Sonner): zastąpione natywnymi (`<dialog>`, `<select>`, `overflow-auto`) i własnym `Toaster`; `DropdownMenu` z `modal={false}`.

## Konsekwencje

Naruszenia CSP wychodzą w teście Playwright (zdarzenie `securitypolicyviolation`), a osobny test potwierdza, że wstrzyknięty skrypt jest blokowany. Własne komponenty to dodatkowy koszt utrzymania.

## Odrzucone opcje

CSP z `unsafe-inline` dla stylów (osłabia ochronę); nonce (wymaga renderowania serwerowego).
