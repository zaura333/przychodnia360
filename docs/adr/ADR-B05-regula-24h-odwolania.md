# ADR-B05: Reguła 24 h odwołania wizyty

Status: przyjęte (etap 0 backendu, 2026-11-14; wdrożenie w 1c)

## Kontekst

Pacjent może odwołać wizytę najpóźniej 24 h przed jej początkiem (FR-04). Rejestrator może odwołać bez tego limitu (FR-51). Wartość limitu może się zmieniać i ma być łatwa do sprawdzenia ręcznie w demo.

## Decyzja

- Reguła żyje w warstwie aplikacji, w `CancellationPolicy`, nie w bazie: zależy od roli wywołującego i od zegara.
- Odwołanie przez pacjenta jest dozwolone, gdy `startAt - now >= okno`. Dokładnie 24:00:00 jest jeszcze dozwolone, 23:59:59 już nie. Porównanie jest na `Instant` (24 h zegarowe, nie "jutro o tej samej godzinie"), więc zmiana czasu nie przesuwa granicy.
- Okno jest konfigurowalne: `appointments.cancellation-window` (domyślnie `PT24H`). `Clock` jest wstrzykiwany, więc testy sterują czasem.
- Rejestrator omija tylko okno 24 h. Wymóg, żeby wizyta była w przyszłości i miała status `SCHEDULED`, obowiązuje wszystkich (`422`). Zamknięte okno daje `422` z typem `/problems/cancellation-window-closed` i polami `earliestAllowed`, `windowHours`.
- Własność wizyty: pacjent odwołuje tylko własne, cudze to `404`.

## Konsekwencje

- Testy graniczne (48 h, 24:00:00, 23:59:59, przeszłość, dni zmiany czasu) są wymagane w 1c.
- Seed zawiera wizyty po obu stronach granicy do ręcznej weryfikacji.

## Odrzucone opcje

- Reguła w bazie (trigger lub CHECK): nie zna roli ani konfiguracji, trudna do testowania zegarem.
- Stała w kodzie: zmiana limitu wymagałaby wdrożenia.
- Odwoływanie przez administratora i lekarza: poza zakresem, odwołuje tylko pacjent i rejestrator.
