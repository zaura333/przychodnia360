# ADR-M16: Agregat OpenAPI składany z eksportów usług

Status: przyjęte (2026-11-15; zlecenie właściciela: sprzątanie modułów wydzielonych z `clinic-core`).

## Kontekst

`api/openapi.json` jest kontraktem klienta frontendu (Orval) i podstawą blokady zmian łamiących (`oasdiff`). Do podziału
`clinic-core` powstawał z kontrolerów jednej aplikacji (`OpenApiExportTest`), a pliki per usługa (`api/services/*`) były
z niego wyprowadzane według tabeli routingu. Po wydzieleniu `catalog` i `patient` ich kontrolery nie istnieją już w
`clinic-core`, więc usunięcie modułów usunęłoby ścieżki z kontraktu frontendu i zablokowało `oasdiff`.

## Decyzja

1. **Każda usługa eksportuje własny kontrakt** do `api/exports/<usługa>.json` (test `OpenApiExportTest` w usłudze), bez
   ścieżek wewnętrznych `/api/v1/internal/**` i bez schematów osiągalnych tylko z nich.
2. **Agregat `api/openapi.json` jest scalaniem eksportów** (`OpenApiAggregate` w `p360-test-support`): ścieżki i komponenty
   w kolejności alfabetycznej, nagłówek z pierwszego eksportu. Duplikaty (sondy zdrowia, wspólne schematy) muszą być
   identyczne, inaczej scalanie kończy się błędem, a nie cichym wyborem wersji.
3. **Każda usługa po własnym eksporcie przelicza agregat** z (swojego świeżego i zatwierdzonych cudzych) eksportów, więc
   zadanie CI dowolnej usługi wykrywa rozjazd (`git diff --exit-code -- api/`) bez zmian w planerze macierzy. W reaktorze
   `clinic-core` jest ostatnim modułem, dzięki czemu pełny `verify` kończy się agregatem ze wszystkich świeżych eksportów.
4. Pliki per usługa logiczną (`api/services/{iam,patient,catalog,scheduling}.openapi.json`) nadal powstają z agregatu
   według tabeli routingu (`OpenApiPerServiceTest`); dla `catalog` i `patient` są równe eksportowi usługi.
5. Liczby dziesiętne są parsowane jako `BigDecimal`, żeby zapis z eksportu (`0.00`) nie zmieniał się przy scalaniu.

## Konsekwencje

- Zmiana API usługi wymaga `./mvnw verify` w `backend/` (aktualizuje eksport i agregat) i zatwierdzenia obu plików.
- Agregat nie zależy już od tego, które usługi hostuje `clinic-core`; kolejne usługi dodają tylko `OpenApiExportTest`.
- Osobny test `OpenApiAggregateTest` pilnuje scalania (duplikaty, konflikty, filtrowanie ścieżek wewnętrznych).
- Agregat odtworzony z eksportów był bajt w bajt równy poprzedniemu (sprawdzone przy wprowadzeniu zmiany).

## Odrzucone opcje

- **Scalanie w osobnym zadaniu CI:** wymagałoby zmiany planera macierzy (zmiana usługi musiałaby uruchamiać agregator).
- **Agregat generowany tylko w `clinic-core`:** rozjazd po zmianie `catalog` lub `patient` wychodziłby dopiero po
  uruchomieniu zadania `clinic-core`, którego zmiana tylko w tych usługach nie uruchamia.
