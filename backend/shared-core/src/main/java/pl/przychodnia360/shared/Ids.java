package pl.przychodnia360.shared;

import java.security.SecureRandom;
import java.util.UUID;

/** Generator identyfikatorów UUIDv7 (kolejność czasowa, dobra lokalność indeksów). */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static UUID newId() {
        return newId(System.currentTimeMillis());
    }

    /** UUIDv7: 48 bitów czasu (ms), wersja 7, wariant RFC 4122, reszta losowa. */
    public static UUID newId(long epochMillis) {
        long msb = (epochMillis & 0xFFFFFFFFFFFFL) << 16;
        long rand = RANDOM.nextLong();
        msb |= 0x7000L | ((rand >>> 52) & 0x0FFFL);
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
