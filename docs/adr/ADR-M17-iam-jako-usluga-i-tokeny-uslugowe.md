# ADR-M17: iam jako osobna usługa, tokeny usługowe od iam i scheduling jako reszta clinic-core

Status: przyjęte (2026-11-15; S3 i S4 z planu podziału `clinic-core`).

## Kontekst

Po S1 (`catalog`) i S2 (`patient`) w `clinic-core` zostały moduły `identity` i `tenant` (placówki, konta, logowanie,
klucz podpisu tokenów) oraz `staff` i `appointments` (pracownicy, grafiki, wizyty). `clinic-core` sam podpisywał tokeny
usługowe (`JwtService.issueService`) do rozmów z `catalog` i `patient`. Po wydzieleniu `iam` klucz prywatny zostaje
wyłącznie w `iam`, więc pozostałe usługi potrzebują innego sposobu, żeby dostać token usługowy, a zakładanie i
dezaktywacja pracownika obejmują dwie usługi (`scheduling` i `iam`).

## Decyzja

1. **`iam` jest osobną usługą** (`backend/services/iam`, baza `p360_iam`, port 8084): placówki, konta, logowanie i
   odświeżanie, JWKS, administracja platformy, e-maile kont (Mailpit lokalnie), saga rejestracji pacjenta do `patient`.
   Krawędź kieruje do niej `/auth/**`, `/me`, `/tenant`, `/facilities` i `/platform/**`.
2. **Tokeny usługowe wydaje `iam` (client credentials)**: `POST /api/v1/internal/service-tokens` z nazwą usługi,
   sekretem, placówką i usługą docelową. Sekrety i dozwolone usługi docelowe są w konfiguracji `iam`
   (`p360.service-clients.<nazwa>.secret|audiences`), sekret porównywany w stałym czasie, placówka musi istnieć i być
   aktywna. Token żyje 60 s, ma rolę `SERVICE`, `aud` usługi docelowej, `tid` placówki oraz podmiot i claim `svc`
   wołającej usługi (w logach widać, kto wołał). Usługi trzymają tokeny w pamięci do chwili tuż przed wygaśnięciem
   (`ServiceTokenClient` w starterze). `iam` sam podpisuje swoje tokeny usługowe (saga do `patient`).
3. **`iam` przyjmuje tokeny usługowe tylko z `aud = iam`** i tylko dla wewnętrznego API kont (uprawnienie
   `IDENTITY_INTERNAL_WRITE`, wyłącznie rola `SERVICE`).
4. **Saga zakładania pracownika** w `scheduling`: identyfikator pracownika jest deterministyczny
   (`SubjectIds.employee(placówka, e-mail)`), `PUT /internal/employee-accounts/{employeeId}` w `iam` jest idempotentne
   (zakłada konto od razu powiązane z pracownikiem), potem lokalny zapis pracownika. Awaria `iam` wycofuje całość (503),
   powtórzenie po utraconej odpowiedzi trafia w to samo konto bez drugiego e-maila. Dezaktywacja: lokalna blokada
   przez przyszłe wizyty, potem idempotentne `POST /internal/users/{id}/deactivate`.
5. **Ścieżki `/api/v1/internal/**` nie są routowane przez krawędź** i nie trafiają do kontraktu publicznego (ADR-M16).
6. **`clinic-core` staje się `scheduling`** (moduł, artefakt, obraz, usługa w compose), ale **zachowuje bazę `p360`,
   role `p360_owner`/`p360_app` i łańcuch migracji** (ADR-M12: bez zmian historii migracji w istniejącej bazie). V13
   usuwa tabele kont i placówek z tą samą ochroną przed utratą danych co V12.
7. **Seed jest podzielony**: `iam` zakłada placówki, konta pacjentów (saga) i konta pracowników z hasłem (z
   deterministycznym identyfikatorem pracownika), administratora na końcu; `scheduling` loguje się jako administrator
   placówki i zakłada katalog, pracowników (saga trafia w przygotowane konta), grafiki, pacjenta bez konta i wizyty.

## Konsekwencje

- Każda nowa usługa wołająca inne usługi dostaje wpis w `p360.service-clients` w `iam` i własny sekret
  (`P360_<USŁUGA>_CLIENT_SECRET`); w AWS sekrety z Parameter Store.
- `iam` jest zależnością logowania i zakładania kont, ale nie rezerwacji: przy wyłączonym `iam` działają rezerwacje i
  widoki z ważnym tokenem (klucze JWKS w pamięci usług), a operacje na kontach dają 503.
- Lokalnie `iam` generuje klucz podpisu przy starcie, więc jego restart unieważnia wydane tokeny (jak wcześniej
  restart `clinic-core`).
- Dług: sekret usługi jest współdzielonym sekretem (nie mTLS ani podpisany JWT klienta); brak rotacji sekretów i
  odwołania pojedynczego tokenu usługowego przed wygaśnięciem; nazwa bazy `p360` nie mówi, że należy do `scheduling`
  (zmiana wymagałaby nowego łańcucha migracji albo kopiowania danych).
- Odrzucone: przekazywanie tokenu użytkownika do `iam` (operacje na kontach w tle i w seedzie nie mają użytkownika,
  a uprawnienia użytkownika przechodziłyby dalej), osobny klucz podpisu w każdej usłudze (wiele JWKS do zaufania),
  mTLS (koszt operacyjny, ADR-M04).
