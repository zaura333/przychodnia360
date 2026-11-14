# ADR-F04: Komponent terminarza

Status: przyjęte (etap 0 frontendu, 2026-11-14)

## Kontekst

Rejestrator ma widzieć w jednym widoku wielu lekarzy naraz (do 30 kolumn, krok 15 minut, doba 07:00-19:00, czyli 48 wierszy i około 1440 komórek). To największe ryzyko UX frontendu (plan frontendu etapu 0, F0.8), więc rozstrzygamy je przed ekranami 1a. Porównano trzy warianty na tych samych danych próbnych (30 wykonawców, około 250 wizyt, dyżury z przerwami, kilka odwołanych; generator `shared/test/scheduleData.ts`, deterministyczny).

## Decyzja

**Wariant A: własna siatka (`shared/ui/schedule-grid`)** dla widoku rejestratora (wielu wykonawców w dobie) i dla widoku lekarza (jeden wykonawca na 7 dni; kolumna ma własne pole `day`). Nie używamy FullCalendar ani react-big-calendar.

Wersja A zostaje w repozytorium jako baza dla zadania F10 (1a). Spike B (react-big-calendar) jest na gałęzi `experiment/schedule-grid-alternatives` (nie do scalania).

## Pomiary

Chromium (Playwright) na buildzie produkcyjnym (`vite build` z `VITE_BENCH=true`, `vite preview` z prawdziwymi nagłówkami CSP), laptop deweloperski, mediana z 7 uruchomień na świeżej stronie; render liczony od kliknięcia do drugiej klatki po zatwierdzeniu. Powtórzenie: `npm run bench:schedule`. Wyniki zmieniają się o kilkadziesiąt procent między uruchomieniami, więc to rząd wielkości, nie dokładne wartości.

| Kryterium (próg informacyjny)           | A: własna siatka                                                                                                                                                        | B: react-big-calendar                                                                                                                                                   |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Pierwszy render, 12 kolumn (< 150 ms)   | 47-73 ms                                                                                                                                                                | 177 ms (próbki 79-258 ms)                                                                                                                                               |
| Pierwszy render, 30 kolumn (< 250 ms)   | 45-53 ms (wirtualizacja kolumn)                                                                                                                                         | 256 ms, 4289 węzłów DOM                                                                                                                                                 |
| Reakcja na ruch fokusu i klik (< 50 ms) | 33 ms (dolna granica pomiaru: dwie klatki)                                                                                                                              | nie mierzone (patrz dostępność)                                                                                                                                         |
| Przewijanie bez throttlingu             | 17 ms na klatkę, 0 klatek > 50 ms                                                                                                                                       | nie mierzone                                                                                                                                                            |
| Render pod CPU 4x wolniej               | 430-620 ms                                                                                                                                                              | nie mierzone                                                                                                                                                            |
| Reakcja pod CPU 4x                      | fokus mediana 33 ms, klik mediana 44-50 ms (max do 150 ms)                                                                                                              | nie mierzone                                                                                                                                                            |
| Przewijanie pod CPU 4x                  | **mediana 63-84 ms na klatkę, 34-42 z 60 klatek > 50 ms** (nie spełnia)                                                                                                 | nie mierzone                                                                                                                                                            |
| Dostępność                              | ARIA `grid`, ruchomy tabindex (1 komórka w kolejności tabulacji), strzałki, Home/End, PageUp/PageDown, Enter, Escape; axe: 0 naruszeń (jsdom i przeglądarka, 30 kolumn) | brak roli `grid`; 270 wydarzeń z `tabindex=0` (270 przystanków Tab, bez poruszania strzałkami); axe: `aria-required-children` i `aria-required-parent` (30 + 30 węzłów) |
| Zgodność z CSP                          | 0 naruszeń `securitypolicyviolation`, brak nowych znaczników `<style>`; pozycje bloków ustawiane przez CSSOM (React `style`), co CSP `style-src 'self'` dopuszcza       | 0 naruszeń (statyczny plik CSS), pozycje też przez `style`                                                                                                              |
| Wygląd w obu motywach                   | tokeny (`bg-background`, `bg-muted`, `bg-primary`...), bez `!important`                                                                                                 | własny arkusz `.rbc-*`, wymagałby przepisania pod tokeny                                                                                                                |
| Polska lokalizacja                      | własne etykiety                                                                                                                                                         | wymaga lokalizatora date-fns (zrobione w spike)                                                                                                                         |

**Wariant C (FullCalendar, wersja darmowa):** `@fullcalendar/core` i `@fullcalendar/timegrid` mają licencję MIT, ale widok zasobów (`@fullcalendar/resource-timegrid`, `resource-timeline`) ma licencję `SEE LICENSE IN LICENSE.md` (płatny Scheduler). Brak widoku zasobów w wersji darmowej potwierdzony, więc odrzucony dla rejestratora bez pomiarów. Wystarczyłby dla jednej kolumny na tydzień, ale ten widok pokrywa własna siatka.

## Konsekwencje

- Własna siatka daje kontrolę nad dostępnością, tokenami i CSP kosztem utrzymania; koszt implementacji prototypu był niewielki (jeden komponent, około 300 linii, 15 testów).
- Wirtualizacja kolumn włącza się powyżej 12 kolumn (`VIRTUALIZE_ABOVE`); wiersze nie są wirtualizowane (48).
- **Ograniczenie: przewijanie poziome pod CPU 4x wolniej nie jest płynne** (około 12-15 klatek na sekundę). Memoizacja komórek (`React.memo`) nie zmieniła tego istotnie; próba `contain: paint` i grupowania aktualizacji w `requestAnimationFrame` nie pomogła i została cofnięta. Prawdopodobna przyczyna to koszt układu przeglądarki dla dużej siatki z elementami `sticky`, nie renderowanie Reacta. Działania w 1a, jeśli okaże się to problemem na docelowych urządzeniach: mniej widocznych kolumn domyślnie, ukrywanie wierszy poza godzinami pracy placówki, `content-visibility`, rezygnacja z `display: contents` dla wierszy.
- Dane testowe zakładają, że widok nie obejmuje nocy zmiany czasu (slot = baza + n * krok); to obowiązuje dla godzin 07:00-19:00.
- Do uwzględnienia w 1a: przeciąganie wizyt nie jest zaimplementowane; odwołane wizyty nie zajmują slotu; wizyty nieleżące na granicy slotu są zaokrąglane do siatki (początek w dół, koniec w górę); zaznaczenie wielu slotów; kolory statusów poza `booked` i `completed`.
- Pomiary w jsdom (axe, testy) nie zastępują pomiarów w przeglądarce; stąd osobny harness `bench:schedule` poza zwykłym `test:e2e`.

## Odrzucone opcje

- **react-big-calendar:** render 12 kolumn nieznacznie powyżej progu, 30 kolumn na progu bez wirtualizacji (4289 węzłów DOM), brak wzorca ARIA `grid` i 270 przystanków tabulacji, naruszenia axe, własny arkusz stylów do przepisania pod tokeny.
- **FullCalendar:** brak widoku zasobów w wersji darmowej (licencja Scheduler).
