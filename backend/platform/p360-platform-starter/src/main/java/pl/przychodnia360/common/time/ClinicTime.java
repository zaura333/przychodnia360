package pl.przychodnia360.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Jedyne miejsce przeliczeń między czasem lokalnym placówki a czasem bezwzględnym (ADR-B04).
 * Grafiki są zapisane lokalnie, wizyty w Instant. Nieistniejąca godzina (przejście na czas letni) przesuwa się
 * do przodu, powtórzona godzina (przejście na zimowy) oznacza wcześniejsze wystąpienie.
 */
public final class ClinicTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private ClinicTime() {
    }

    public static Instant toInstant(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, ZONE).toInstant();
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    /** Początek następnego dnia lokalnego (koniec wyłączny dnia o długości 23, 24 lub 25 godzin). */
    public static Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }
}
