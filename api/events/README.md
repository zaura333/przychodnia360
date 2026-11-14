# Kontrakty zdarzeń

Schematy (JSON Schema 2020-12) zdarzeń wymienianych między usługami przez outbox, SNS i SQS (ADR-M03).

- `envelope.schema.json`: koperta wspólna (`eventId`, `type`, `schemaVersion`, `tenantId`, `aggregateId`, `occurredAt`, `traceparent`, `payload`).
- `<TypZdarzenia>.schema.json`: schemat ładunku danego zdarzenia. `x-schemaVersion` musi być równe `schemaVersion()` rekordu zdarzenia, a `x-producer` wskazuje usługę, która je publikuje. `$id` jest bezwzględnym identyfikatorem (`https://schemas.przychodnia360.example/events/<Typ>.schema.json`; domena `.example` jest zarezerwowana, schematy nie są publikowane pod tym adresem).
- `consumers/<konsument>/<TypZdarzenia>.json`: **oczekiwania konsumenta** wobec zdarzenia (pola i typy, których używa). Test sprawdza, że schemat dostawcy je spełnia.

## Zasady zmian

- **Zmiana zgodna wstecz** (można w tym samym pliku): nowe pole opcjonalne lub nowe pole wymagane (konsumenci muszą ignorować nieznane pola), zawężenie.
- **Zmiana łamiąca** (zabroniona w tym samym pliku, CI ją blokuje): usunięcie pola, zmiana typu lub formatu pola, pole wymagane staje się opcjonalne, obniżenie `x-schemaVersion`. Zamiast tego: nowy typ zdarzenia (np. `AppointmentBookedV2`) publikowany równolegle do czasu migracji konsumentów.
- Zdarzenia niosą wyłącznie identyfikatory i dane techniczne, bez danych osobowych (ADR-M10; pilnuje reguła ArchUnit).

Testy: `DomainEventSchemaTest` (każdy rekord `DomainEvent` ma schemat i serializuje się zgodnie z nim), `EventSchemaBreakingChangeTest` (zmiany względem gałęzi bazowej w CI), `EventConsumerContractsTest` (oczekiwania konsumentów).
