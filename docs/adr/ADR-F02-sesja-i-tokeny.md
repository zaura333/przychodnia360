# ADR-F02: Sesja i tokeny

Status: przyjęte (etap 0 frontendu, 2026-11-13)

## Kontekst

Dane medyczne wymagają, żeby poświadczenia nie były dostępne dla skryptów ani trwale zapisane w przeglądarce.

## Decyzja

Token dostępu wyłącznie w pamięci (`tokenStore`, zmienna modułu z subskrypcją). Sesję odtwarza ciasteczko `HttpOnly` (`p360_rt`, `Path=/api/v1/auth`) przez `POST /auth/refresh`. Odświeżanie jest single-flight (jedno wspólne wywołanie dla równoległych `401`), z jednorazowym ponowieniem żądania i bez odświeżania dla `/auth/*` (ochrona przed pętlą). Po porażce zdarzenie `sessionExpired`. `AuthProvider` ma stany `loading`, `anonymous`, `authenticated`; błąd sieci lub 5xx przy starcie daje `anonymous` z flagą `backendUnavailable`. Cache zapytań jest czyszczony przy wylogowaniu, wygaśnięciu sesji i zmianie użytkownika; klucze zapytań zawierają `userId`; brak persistera.

## Konsekwencje

Po przeładowaniu strony każda sesja wymaga jednego wywołania `refresh` (ciche, ekran startowy bez migania logowania). W StrictMode odświeżenie jest zabezpieczone przed podwójnym wywołaniem, bo obraca ciasteczko.

## Odrzucone opcje

Token w `localStorage` (dostępny dla XSS); sesja ciasteczkowa bez tokenu (wymaga CSRF i nie pasuje do bezstanowej Lambdy).
