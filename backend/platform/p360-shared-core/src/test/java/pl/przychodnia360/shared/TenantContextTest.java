package pl.przychodnia360.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    private final TenantId a = new TenantId(UUID.randomUUID());
    private final TenantId b = new TenantId(UUID.randomUUID());

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void emptyByDefault() {
        assertThat(TenantContext.current()).isEmpty();
        assertThat(TenantContext.isExplicitlyWithoutTenant()).isFalse();
    }

    @Test
    void openAndCloseRestoresPreviousState() {
        try (var outer = TenantContext.open(a)) {
            assertThat(TenantContext.current()).contains(a);
            try (var inner = TenantContext.open(b)) {
                assertThat(TenantContext.current()).contains(b);
            }
            assertThat(TenantContext.current()).contains(a);
        }
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void explicitWithoutTenantIsDistinctFromMissing() {
        try (var h = TenantContext.openWithoutTenant()) {
            assertThat(TenantContext.current()).isEmpty();
            assertThat(TenantContext.isExplicitlyWithoutTenant()).isTrue();
        }
        assertThat(TenantContext.isExplicitlyWithoutTenant()).isFalse();
    }

    @Test
    void doubleCloseIsHarmless() {
        var h = TenantContext.open(a);
        h.close();
        h.close();
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void doesNotLeakToOtherThreads() throws Exception {
        try (var h = TenantContext.open(a)) {
            var seen = new AtomicReference<Object>("unset");
            Thread t = new Thread(() -> seen.set(TenantContext.current().orElse(null)));
            t.start();
            t.join();
            assertThat(seen.get()).isNull();
        }
    }

    @Test
    void rejectsNullTenant() {
        assertThatThrownBy(() -> TenantContext.open(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
