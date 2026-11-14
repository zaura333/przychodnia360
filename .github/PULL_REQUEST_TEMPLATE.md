## Co i dlaczego

<!-- Krótki opis zmiany i powiązane zadanie z planu. -->

## Lista kontrolna

- [ ] **Migracje:** zgodne wstecz (expand/contract), istniejące skrypty Flyway niezmienione
- [ ] **RLS:** każda nowa tabela ma `tenant_id`, `ENABLE`/`FORCE ROW LEVEL SECURITY` i politykę (albo nie dotyczy)
- [ ] **Macierz uprawnień:** każdy nowy endpoint ma wpis w macierzy ról (albo nie dotyczy)
- [ ] **Kontrakt:** `api/openapi.json` zregenerowany i zatwierdzony, zmiany łamiące opisane (albo nie dotyczy)
- [ ] **Brak PII:** żadnych rzeczywistych danych osobowych ani medycznych, w tym w logach, fixture'ach i zrzutach ekranu
- [ ] **Sekrety:** brak sekretów w kodzie i konfiguracji
- [ ] **Testy:** dodane lub zaktualizowane
