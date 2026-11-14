# ADR-B06: Haszowanie haseł Argon2id (wstępnie)

Status: wstępne (etap 0 backendu, 2026-11-14); parametry ostateczne po pomiarze Z0.8 (w numeracji planu to ADR-007)

## Kontekst

Hasła użytkowników muszą być haszowane algorytmem odpornym na ataki sprzętowe. Zbyt kosztowne parametry spowalniają logowanie i zjadają pamięć małego zadania Fargate (0,5 vCPU, 2 GB), zbyt słabe obniżają odporność na złamanie hasha.

## Decyzja

- Algorytm: **Argon2id**, implementacja Spring Security `Argon2PasswordEncoder` (Bouncy Castle). Parametry są zakodowane w haszu, więc zmiana parametrów nie unieważnia istniejących haseł (rehash przy udanym logowaniu).
- Parametry startowe (wg zaleceń OWASP): pamięć 19 MiB, 2 iteracje, równoległość 1, sól 16 B, hasz 32 B. Cel czasowy 200-500 ms na docelowym CPU.
- Parametry są w konfiguracji (`security.password.argon2.*`), nie w kodzie.
- Polityka haseł: długość >= 12, bez wymogu klas znaków, odrzucenie listy najpopularniejszych haseł.
- Logowanie ma limit prób, a nieistniejące konto kosztuje tyle samo czasu co istniejące (ADR-B02), żeby koszt haszowania nie był kanałem wyliczania kont.
- Zużycie pamięci przy równoległych logowaniach to liczba logowań x pamięć Argon2, więc liczba jednoczesnych haszowań jest ograniczona.

## Konsekwencje

- Ostateczne parametry i rozmiar zadania wynikną z pomiaru na Fargate (Z0.8) i trafią do ADR-006 i ADR-007. Do tego czasu docker-compose używa limitów 0,5 vCPU i 2 GB, żeby objawy były widoczne wcześnie.
- Testy używają obniżonych parametrów (profil `test`), żeby nie wydłużać builda.

## Odrzucone opcje

- bcrypt: limit 72 bajtów hasła i brak twardości pamięciowej.
- PBKDF2: słabszy wobec GPU przy podobnym koszcie.
- Parametry "na zapas" bez pomiaru: ryzyko wolnego logowania lub braku pamięci na małym zadaniu.
