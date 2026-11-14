# ADR-B07: Tokeny dostępu (JWT HS256) i odświeżanie z rotacją

Status: przyjęte (etap 1a, 2026-11-14); **zmienione 2026-11-14 (ADR-M04, zadanie 1.2): podpis RS256 zamiast HS256, patrz sekcja „Zmiana" na końcu**

## Kontekst

API jest bezstanowe, a przeglądarka trzyma token dostępu tylko w pamięci (ADR-F02). Sesja musi przeżyć odświeżenie strony, więc potrzebny jest długożyjący token odświeżania, którego nie przeczyta JavaScript. Wystawcą i weryfikatorem tokenów jest ta sama aplikacja.

## Decyzja

**Token dostępu.** JWT podpisany HS256 (klucz symetryczny wystarcza, bo nie ma zewnętrznych weryfikatorów), nagłówek `kid`, czas życia 10 minut. Claimy: `sub` (użytkownik), `tid` (placówka, brak dla administratora platformy), `role`, `eid` i `pid` (powiązanie z pracownikiem lub pacjentem), `iss`, `iat`, `exp`. Weryfikacja odrzuca inny algorytm niż HS256, nieznany `kid`, zły podpis, innego wystawcę i token wygasły. Klucze (min. 32 bajty, Base64) są w konfiguracji `security.jwt.keys`, podpisuje `active-kid`, a weryfikować można każdym kluczem z listy, co daje rotację bez wylogowania użytkowników. W AWS klucze pochodzą z Parameter Store (zmienne `JWT_ACTIVE_KID`, `JWT_ACTIVE_KEY`).

**Kontekst placówki** wynika wyłącznie z tokenu: filtr ustawia `TenantContext` na czas żądania, a identyfikatory placówek z ścieżki lub ciała nigdy go nie zmieniają.

**Token odświeżania.** Losowe 256 bitów w ciasteczku `p360_rt` (`HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`), ważne 7 dni. Format `<tenantId lub platform>.<familyId>.<losowa>`: placówka i rodzina są jawne, żeby ustawić kontekst RLS przed odczytem bez dodatkowych uprawnień funkcji bazodanowych, a wartość jest tajna. W bazie jest tylko SHA-256 całego tokenu. Każde odświeżenie zużywa token i wydaje nowy w tej samej rodzinie (rotacja). Ponowne użycie zużytego lub unieważnionego tokenu unieważnia całą rodzinę (sygnał kradzieży). Odczyt tokenu jest z blokadą wiersza, więc dwa równoległe odświeżenia tym samym tokenem nie przejdą oba.

**Unieważnianie.** Wylogowanie unieważnia rodzinę. Zmiana lub reset hasła i dezaktywacja konta unieważniają wszystkie tokeny użytkownika. Konto zablokowane lub nieaktywne nie odświeża sesji.

**Ochrona ciasteczka przed CSRF** (D-029): `SameSite=Strict`, sprawdzenie nagłówka `Origin` na `/auth/refresh` i `/auth/logout` względem `security.allowed-origins` oraz wąska ścieżka ciasteczka.

**Limity i blokady.** Blokada konta po 5 nieudanych logowaniach na 15 minut (konfigurowalne), licznik zapisywany w osobnej transakcji, żeby wyjątek 401 go nie wycofał. Limiter w pamięci na `/auth/*` (IP i e-mail) jako druga warstwa.

## Konsekwencje

- Odwołanie dostępu działa po wygaśnięciu tokenu dostępu (do 10 minut), a od razu dla odświeżania. Token po dezaktywacji konta zachowuje ważność do wygaśnięcia; kontrolę statusu przy każdym żądaniu można dodać, jeśli okaże się potrzebna (koszt: odczyt z bazy na żądanie).
- Limiter w pamięci zeruje się po restarcie zadania; głównym zabezpieczeniem kont jest blokada w bazie.
- Zmiana klucza aktywnego: dodać nowy klucz, przestawić `active-kid`, po 10 minutach usunąć stary.

## Odrzucone opcje

- RS256 lub EdDSA: asymetria nie jest potrzebna, gdy token weryfikuje jego wystawca.
- Token odświeżania w `localStorage`: dostępny dla skryptów (XSS).
- Odświeżanie bez rotacji: kradzież tokenu daje 7 dni dostępu bez śladu.
- Sesje serwerowe: wymagają współdzielonego magazynu sesji, zbędnego przy jednym zadaniu i modelu bezstanowym.

## Zmiana (2026-11-14, ADR-M04, zadanie 1.2): podpis asymetryczny RS256 i JWKS

Podział na usługi wymaga, żeby tokeny weryfikowała każda usługa bez wspólnego sekretu, więc symetryczny HS256 został zastąpiony przez **RS256** (RSA, klucz co najmniej 2048 bitów). Wybór RS256, a nie EdDSA, wynika z natywnej obsługi w JDK i Nimbus (EdDSA wymagałby dodatkowej biblioteki Tink) i zamyka decyzję otwartą w ADR-M04.

- **Wystawca (iam)** podpisuje kluczem prywatnym i publikuje klucze publiczne w `GET /api/v1/auth/jwks` (publiczny, tylko parametry publiczne, także klucze wycofywane po rotacji). Pozostałe usługi mają wyłącznie adres JWKS (`security.jwt.jwks-url`), nie mają żadnego sekretu.
- **Weryfikacja** (`RsaAccessTokenVerifier` w starterze) wymaga algorytmu RS256, więc odrzuca `none`, HS* (w tym klasyczny atak z podpisem HMAC kluczem publicznym) i inne algorytmy; klucz wynika z `kid`; następnie sprawdzane są podpis, wystawca, `exp`, obecność `iat` i lista odwołań (ADR-M04).
- **Klucze** (`security.jwt.keys.<kid>.private-key` PKCS#8 i `public-key` X.509, Base64): aktywny klucz musi mieć część prywatną; wycofywane po rotacji wystarczy podać wyłącznie publicznie. W profilach `local` i `test` klucz jest generowany przy starcie (`generate-ephemeral-key`), więc w repozytorium nie ma kluczy prywatnych; w profilu `demo` klucz prywatny pochodzi z Parameter Store (`JWT_ACTIVE_KID`, `JWT_ACTIVE_KEY`), a brak kluczy zatrzymuje start.
- **Rotacja:** nowy klucz dochodzi jako aktywny, poprzedni zostaje w JWKS jako publiczny na czas życia tokenów dostępu (10 minut) i tokenów w obiegu. Konsumenci JWKS buforują zestaw (5 minut) i po nieznanym `kid` pobierają go ponownie z ograniczeniem częstotliwości; przy niedostępności wystawcy używają ostatniego zestawu.
- Pozostałe ustalenia tego ADR (czas życia tokenu 10 minut, claimy, token odświeżania, unieważnianie, ochrona przed CSRF) bez zmian.
