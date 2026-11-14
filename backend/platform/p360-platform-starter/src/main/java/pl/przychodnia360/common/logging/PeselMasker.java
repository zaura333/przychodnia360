package pl.przychodnia360.common.logging;

import java.util.regex.Pattern;

/** Maskuje ciągi 11 cyfr (PESEL) w tekście logów. Reguła defensywna: dane pacjenta i tak nie trafiają do logów. */
public final class PeselMasker {

    private static final Pattern PESEL = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])");

    private PeselMasker() {
    }

    public static String mask(String text) {
        return text == null ? null : PESEL.matcher(text).replaceAll("***");
    }
}
