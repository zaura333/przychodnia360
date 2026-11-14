# ADR-M04: Tożsamość usług i odwołanie dostępu

Status: przyjęte warunkowo (fala 0R, 2026-11-14; zlecenie właściciela „zrób 0R"). Część JWKS i token usługowy dojdzie w fali 1.2 i wymaga potwierdzenia po spike'u.

## Kontekst

Dziś token dostępu (JWT HS256, 10 minut, ADR-B07) weryfikuje ta sama aplikacja, która go wystawia, a status użytkownika jest sprawdzany tylko przy odświeżeniu sesji. Dezaktywowany użytkownik ma więc dostęp do wygaśnięcia tokenu (do 10 minut; luka D-032). Po podziale na usługi (fala 1.2: JWT asymetryczny, JWKS) usługi inne niż IAM nie mogą sprawdzić statusu w bazie IAM, więc sposób odwoływania dostępu trzeba wybrać przed zmianą tokenu.

## Decyzja

1. **Odwołanie dostępu zdarzeniem.** Dezaktywacja konta publikuje zdarzenie domenowe `UserDeactivated` (identyfikatory, bez danych osobowych). Każda usługa utrzymuje listę odwołań (`AccessRevocations`): token użytkownika wystawiony (`iat`) nie później niż w chwili odwołania jest odrzucany. Wpis żyje tyle, ile najdłuższy token dostępu plus zapas na rozjazd zegarów.
2. **W fali 0R** zdarzenie jest w procesie (po commit), a listę zasila nasłuchiwanie w module `identity`; weryfikacja tokenu w `JwtService` sprawdza listę. Dostęp ustaje od razu, a nie po 10 minutach. W fali 1.3 publikację przejmuje outbox z dostarczaniem do SNS/SQS; usługi dostają zdarzenie i zasilają własną listę tym samym kodem.
3. **TTL tokenu dostępu bez zmian** (10 minut): lista odwołań jest pierwszą linią, krótki TTL ogranicza wielkość listy i skutki zgubionego zdarzenia.
4. **Fala 1.2 (do potwierdzenia):** podpis asymetryczny (EdDSA lub RS256, decyzja po spike'u zgodności z Nimbus), `kid` i JWKS, rotacja kluczy zachowana; token usługowy z `aud`, `tenant_id`, `on_behalf_of`.

## Konsekwencje

- Zamyka lukę D-032 (status użytkownika sprawdzany dopiero po wygaśnięciu tokenu), bez wywołań synchronicznych do IAM przy każdym żądaniu.
- Lista odwołań jest pamięcią procesu: po restarcie usługi traci wpisy, więc token dezaktywowanego użytkownika może działać do wygaśnięcia (maks. 10 minut). Akceptowalne w demo; do rozważenia: trwałość listy lub nadrzędne sprawdzenie przy odświeżeniu (już działa).
- Utracone zdarzenie `UserDeactivated` (DLQ, awaria) daje to samo okno do 10 minut; DLQ i alarm (fala 1.3) ograniczają ryzyko.
- Test `DeactivationRevokesAccessTest` pilnuje, że token dezaktywowanego użytkownika przestaje działać natychmiast.

## Odrzucone opcje

- **Samo skrócenie TTL (np. 1–2 minuty):** więcej odświeżeń i obciążenie IAM, a okno i tak zostaje.
- **Introspekcja (zapytanie do IAM przy każdym żądaniu):** sprzężenie synchroniczne każdej usługi z IAM (ryzyko rozproszonego monolitu) i dodatkowe opóźnienie.
