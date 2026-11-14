package pl.przychodnia360.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PeselMaskerTest {

    @Test
    void masksElevenDigitSequences() {
        assertThat(PeselMasker.mask("pacjent 44051401359 zapisany")).isEqualTo("pacjent *** zapisany");
        assertThat(PeselMasker.mask("a 44051401359, b 02070803628")).isEqualTo("a ***, b ***");
    }

    @Test
    void leavesOtherNumbersAlone() {
        assertThat(PeselMasker.mask("1234567890")).isEqualTo("1234567890");     // 10 cyfr
        assertThat(PeselMasker.mask("123456789012")).isEqualTo("123456789012"); // 12 cyfr
        assertThat(PeselMasker.mask(null)).isNull();
    }
}
