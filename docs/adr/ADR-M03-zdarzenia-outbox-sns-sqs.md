# ADR-M03: Zdarzenia między usługami: transakcyjny outbox, SNS i SQS

Status: przyjęte (fala 1.3 i 1.4, 2026-11-14; zlecenie właściciela). Do potwierdzenia w CI: działanie LocalStack na runnerze GitHub Actions.

## Kontekst

Plan (`plan-microservices.md`, sekcja 4) zakłada, że fakty (np. wizyta zarezerwowana, konto zdezaktywowane) płyną między usługami asynchronicznie, a usługi nie współdzielą baz. Zapis do bazy i wysłanie wiadomości do brokera nie mogą być jedną transakcją, więc bez dodatkowego mechanizmu zdarzenie ginie przy awarii między tymi krokami albo wychodzi mimo wycofania transakcji (podwójny zapis). Budżet AWS demo (D7) wyklucza własny klaster brokera.

## Decyzja

1. **Transakcyjny outbox.** Zdarzenie jest zapisywane do tabeli `outbox` w tej samej transakcji co zmiana danych (`OutboxDomainEventPublisher`). Poza aktywną transakcją publikacja jest błędem. Wycofanie transakcji usuwa zdarzenie razem ze zmianą. Po commit zdarzenie trafia też do nasłuchujących w procesie (np. odwołanie dostępu, ADR-M04).
2. **Przekaźnik** (`OutboxRelay`) w jednej transakcji bierze nieopublikowane wiersze w kolejności zapisu, wysyła je i oznacza jako opublikowane. Jedna instancja pracuje naraz dzięki `pg_try_advisory_xact_lock`. Błąd wysyłki zatrzymuje partię (kolejność), zwiększa licznik prób i zapisuje błąd; zdarzenie jest ponawiane w następnym przebiegu. Opublikowane wiersze są usuwane po 30 dniach (RC-10).
3. **Dostęp przekaźnika do wszystkich placówek** przez osobne ustawienie sesji `app.relay = 'on'` i politykę RLS `relay_scope` (funkcja `app_relay()`), a nie przez `app_scope()`. Bramka `RlsCoverageTest` wymaga, żeby `app_scope()` znały tylko tabele tożsamości, i dodatkowo, żeby `app_relay()` znał tylko `outbox`.
4. **Transport:** jeden temat SNS na usługę (`p360-<usługa>-events`), jedna kolejka SQS na konsumenta (`p360-<konsument>-events`) z DLQ i subskrypcją z dostawą surową (`RawMessageDelivery`). Abstrakcja `EventTransport` (publikacja) i `SqsEventConsumer` (konsumpcja); bez skonfigurowanego brokera działa `LoggingEventTransport` (zdarzenia tylko logowane). Włączenie: `p360.events.sns.topic-arn`, `p360.events.sqs.queue-url`, `p360.events.aws.endpoint` (LocalStack).
5. **Dostarczanie at-least-once.** Konsument przetwarza wiadomość w kontekście placówki zdarzenia, w jednej transakcji z wpisem inboxu (`processed_events`, klucz `consumer + event_id`) i skutkami obsługi (`InboxGuard`), a dopiero potem kasuje ją z kolejki. Duplikat jest pomijany. Typ bez handlera jest potwierdzany i pomijany (temat jest rozgłoszeniowy).
6. **Błędy konsumenta.** Wyjątek, koperta nieparsowalna lub bez `eventId`, `type` albo `tenantId` nie powodują skasowania wiadomości: wraca po czasie widoczności i po `maxReceiveCount` (domyślnie 5) trafia do DLQ. Izolacja placówek obejmuje zdarzenia: zdarzenie bez `tenantId` nigdy nie jest przetwarzane.
7. **Koperta** (`EventEnvelope`): `eventId`, `type`, `schemaVersion`, `tenantId`, `aggregateId`, `occurredAt`, `traceparent`, `payload`. Zdarzenia niosą wyłącznie identyfikatory i dane techniczne, bez danych osobowych (reguła ArchUnit na rekordach zdarzeń). `schemaVersion` rośnie przy zmianie niezgodnej wstecz.
8. **Lokalnie** LocalStack (obraz przypięty, community, bez tokenu), temat i kolejki z DLQ tworzy `docker/localstack/init/ready.d/01-events.sh`; w testach Testcontainers.

## Konsekwencje

- Gwarancja: zdarzenie powstaje wtedy i tylko wtedy, gdy zatwierdziła się zmiana danych; po awarii przekaźnika nie ginie. Koszt: dodatkowy zapis w transakcji i opóźnienie dostarczenia (domyślnie ok. 1 s, `p360.events.relay.interval`).
- **Kolejność:** zachowana tylko przy publikacji z jednej usługi. Kolejki SQS standardowe nie gwarantują kolejności u konsumenta, więc konsument nie może na niej polegać (zdarzenie ma `occurredAt`; `aggregateVersion` dojdzie w fali 1.8, gdy będzie potrzebny).
- Konsumenci muszą być idempotentni także poza inboxem (retencja `processed_events` jest ograniczona, RC-10).
- Awaria brokera nie zatrzymuje operacji biznesowych: wiersze outboxu czekają, przekaźnik ponawia.
- Przekaźnik w każdej instancji usługi: jedna blokada doradcza na bazę usługi; przy wielu instancjach publikuje jedna naraz.
- Tabele `outbox` i `processed_events` (migracja V10) są wzorcem kopiowanym do każdej usługi (`docs/db-conventions.md`).

## Do zrobienia

- Schematy zdarzeń i koperty w `api/events/` oraz sprawdzanie zgodności w CI (zadanie 1.8).
- ~~`traceparent` w kopercie~~ zrobione w zadaniu 1.6: outbox zapisuje bieżący `traceparent` (kolumna z V11), przekaźnik wkłada go do koperty, a konsument odtwarza go jako rodzica nowego zakresu (`EventTracing`); test na LocalStack. Zostaje eksport śladów do collectora (wybór narzędzia otwarty).
- Alarm długości DLQ i wieku najstarszej wiadomości (observability, 1.6).
- Weryfikacja LocalStack na runnerze CI (obraz ok. 2 GB, test ok. 24 s po pobraniu).

## Odrzucone opcje

- **Publikacja bezpośrednio do SNS w transakcji biznesowej:** podwójny zapis, utrata lub duplikat zdarzenia przy awarii.
- **CDC (np. Debezium) na tabeli outbox:** dodatkowy komponent do utrzymania i budżet, bez korzyści w skali demo.
- **Kafka lub RabbitMQ:** własna infrastruktura i koszt przekraczające budżet D7; SNS + SQS są usługami zarządzanymi i mają emulator.
- **SQS FIFO:** gwarantuje kolejność, ale ogranicza przepustowość i komplikuje subskrypcje SNS; kolejność nie jest wymagana przez żaden przypadek użycia planu.
- **Odpytywanie baz między usługami:** łamie zasadę bazy per usługa (ADR-M05).
