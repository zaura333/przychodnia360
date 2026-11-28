# Plan etapów

Plan rozwoju Przychodni360. To projekt demonstracyjny: wyłącznie dane syntetyczne, usługi P1 są symulowane, a integracje (księgowość, płatności i inne) działają na mockach i sandboxach. Budżet AWS jest mały (ok. 100 USD), więc usługi w chmurze są poza pokazami wyzerowane.

Aktualny stan w skrócie jest w [README](../README.md#status).

## Etapy

| Etap | Zakres | Stan |
| ---- | ------ | ---- |
| **0** | Szkielety, CI, Docker | gotowy |
| **1a – Wizyty** | Konta i logowanie, pracownicy, usługi i grafiki, przegląd i rezerwacja wizyt, harmonogram lekarza, placówki, rejestrator | gotowy |
| **0M – Platforma mikroserwisów** | Starter (security, tenant/RLS, outbox/inbox), JWT asymetryczny z JWKS, krawędź (routing ścieżek), CI macierzowe, kontrakty API i zdarzeń, observability, profile compose, rozbijanie rdzenia 1a na usługi | gotowy |
| **1b – Dokumentacja medyczna** | Historia pacjenta, recepty i skierowania jako dokumenty wewnętrzne, szyfrowanie, dziennik audytu | planowany |
| **1c – Uzupełnienia** | Wymagania Should i Could: wygoda użytkowników, statystyki, panel administratora | planowany |
| **1d – Personel medyczny** | Personel niebędący lekarzami (RTG, pobieranie krwi) wykonuje badania w ramach wizyt, bez recept i skierowań | planowany |
| **2 – Dokumenty i OCR** | Załączniki do historii pacjenta, PDF recept i skierowań z kodem QR, OCR skanów (Tesseract, język polski) jako podpowiedź pól, przetwarzanie asynchroniczne (S3, SQS) | planowany |
| **3 – Gabinety i sprzęt** | Gabinet i kluczowy sprzęt jako zasoby rezerwowalne, eksport czasu pracy (CSV/API), import sprzętu z zewnętrznego systemu ewidencji | planowany |
| **4 – P1 (demo)** | Symulowane usługi P1 za adapterem: e-recepta, następnie e-skierowanie | planowany |
| **5 – Finanse i integracje** | `notification-service`, `integration-hub`, `billing` (należności, płatności, dokumenty sprzedaży, kasa), adaptery (księgowość, KSeF przez księgowość, płatności online), MFA, rola księgowości, raporty finansowe | planowany |
| **R – Polityka retencji danych** | Klasy danych z okresami, niszczenie z zatwierdzeniem i protokołem, blokady, zamknięcie konta, dezaktywacja placówki z eksportem, kopie zapasowe | rozłożona na etapy 0M–5 |

Etapy opcjonalne, realizowane tylko po osobnej decyzji:

| Etap | Zakres |
| ---- | ------ |
| **6 – Rozszerzona klinika** | Diagnostyka (laboratorium, obrazowanie), wideokonsultacje, kolejka, telefonia, terminal płatniczy, podpis elektroniczny, eksport zużycia materiałów do systemu magazynowego |
| **7 – Zaplecze** | NFZ, płatnicy, leki |

## Podział etapu 1 (MVP)

Kryterium podziału: najpierw wszystko, co potrzebne do umówienia wizyty (1a), potem dokumentacja medyczna (1b), uzupełnienia (1c), na końcu personel medyczny (1d).

| Podetap | Cel |
| ------- | --- |
| **1a – Wizyty** | Pacjent zakłada konto, przegląda lekarzy i usługi, rezerwuje wolny termin; lekarz widzi swój harmonogram; administrator konfiguruje placówkę. Rezerwacja bez kolizji, izolacja placówek (JWT z `tenant_id`, RLS), RBAC i macierz uprawnień |
| **1b – Dokumentacja medyczna** | Lekarz prowadzi historię pacjenta i wystawia dokumenty wewnętrzne; szyfrowanie i dziennik audytu; model recept i skierowań przygotowany pod P1 |
| **1c – Uzupełnienia** | Wymagania Should i Could dla pacjenta, lekarza, administratora i rejestratora; test obciążeniowy po zakończeniu 1d |
| **1d – Personel medyczny** | Wykonawca wizyty niebędący lekarzem, wspólna encja pracowników, testy macierzy uprawnień dla roli personelu |

Uwagi:

- Podetap 1a sam w sobie da się zademonstrować (rezerwacja wizyty od końca do końca), więc był dowożony jako pierwszy.
- Placówki (zarządzanie) trafiły do 1a w minimalnej formie, bo bez nich nie da się przetestować izolacji danych.
- Wymagania Could z 1c można w razie opóźnień przesunąć poza MVP bez wpływu na resztę.
- Etap 1 jest realizowany jako usługi: 1a powstało w jednym module (`clinic-core`), rozbijanym w ramach 0M; konteksty od 1b powstają od razu jako usługi.
