# ADR-F08: Kreator rezerwacji i obsługa kolizji

Status: przyjęte (etap 1a frontendu, 2026-11-14)

## Kontekst

Wielu pacjentów i rejestratorów rezerwuje równolegle, a lista wolnych terminów starzeje się w chwili pobrania. O przyznaniu terminu decyduje baza (ograniczenia wykluczające, ADR-B08), więc interfejs musi sobie radzić z odrzuceniem rezerwacji, którą przed chwilą pokazał jako możliwą.

## Decyzja

**Źródłem prawdy jest odpowiedź na `POST /appointments`.** Klient nigdy nie zakłada, że wolny slot zostanie przyznany. Wolne terminy są pobierane tydzień po tygodniu (`from` i `to` to daty włącznie w strefie Warszawy, okno ≤ 32 dni), z `staleTime: 0`, a po każdej rezerwacji, przesunięciu i odwołaniu (także nieudanej) unieważniane są zapytania o sloty, wizyty i grafiki.

**Klasyfikacja błędów po polu `type` Problem Details, nie po treści `detail`** (`classifyBookingError`): `409 slot-taken` i `409 patient-overlap` to dwa różne komunikaty, `422` (poza grafikiem, w przeszłości, wykonawca nie pasuje) odświeża listę i prosi o inny termin, `403` i `404` przenoszą na początek kreatora. Inne błędy pokazuje `describeError`.

**Kreator pacjenta** (usługa, lekarz, termin, potwierdzenie) trzyma stan w jednym komponencie. Powrót zachowuje wybory, a zmiana usługi lub lekarza czyści wybrany termin. Wstępne wypełnienie z adresu (`?serviceId=`, `?employeeId=`) jest odrzucane przy renderowaniu, jeśli identyfikator nie występuje na liście, a nie w efekcie (bez setState w `useEffect`). Konflikt (`409`, `422`) wraca do kroku terminu z odświeżonymi slotami, usługa i lekarz zostają. Przycisk potwierdzenia jest nieaktywny w trakcie wysyłania. `POST` pacjenta nie zawiera `patientId` (wynika z tokenu).

**Terminarz rejestratora i przesunięcie** używają tego samego `SlotPicker` i tej samej klasyfikacji, ale kolizja zostawia dialog otwarty z zachowanym wyborem pacjenta i usługi (rejestrator ma poprawić godzinę, nie zaczynać od nowa).

## Konsekwencje

Pacjent może zobaczyć termin, który za chwilę zniknie; komunikat o kolizji jest częścią normalnego przebiegu, a nie awarią. Test wyścigu po stronie klienta (`booking.test.tsx`) sprawdza powrót do terminu i zniknięcie zajętego slotu z odświeżonej listy; wyścig dwóch przeglądarek na prawdziwym backendzie to e2e.

## Odrzucone opcje

- Blokada slotu na czas wypełniania formularza: wymaga rezerwacji tymczasowych i ich wygaszania po stronie backendu, a ograniczenie w bazie daje to samo bez blokad.
- Optymistyczne zajęcie slotu na liście: pokazuje fałszywy stan przy odrzuceniu.
- Rozpoznawanie kolizji po treści komunikatu: kruche i zależne od języka.
