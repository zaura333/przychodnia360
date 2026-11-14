package pl.przychodnia360.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void generatesVersion7Rfc4122Ids() {
        UUID id = Ids.newId();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void idsAreOrderedByTime() {
        UUID earlier = Ids.newId(1_000_000L);
        UUID later = Ids.newId(2_000_000L);
        assertThat(earlier.toString().compareTo(later.toString())).isNegative();
    }

    @Test
    void idsAreUnique() {
        assertThat(Ids.newId()).isNotEqualTo(Ids.newId());
    }
}
