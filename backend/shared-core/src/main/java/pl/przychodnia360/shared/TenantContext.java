package pl.przychodnia360.shared;

import java.util.Optional;

/**
 * Kontekst placówki bieżącego wątku (żądania). Zawsze zamykany w {@code finally} / try-with-resources.
 *
 * <p>Trzy stany: brak kontekstu (transakcja wymagająca tenanta jest odrzucana), jawnie wybrana placówka
 * oraz jawny brak placówki (logowanie, administrator platformy).
 */
public final class TenantContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Stan kontekstu. {@code tenant == null} oznacza jawny brak placówki. */
    private record Scope(TenantId tenant) {
    }

    /** Zamykany uchwyt przywracający poprzedni stan. */
    public static final class Handle implements AutoCloseable {
        private final Scope previous;
        private boolean closed;

        private Handle(Scope previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static Handle open(TenantId tenant) {
        if (tenant == null) {
            throw new IllegalArgumentException("tenant");
        }
        Handle handle = new Handle(CURRENT.get());
        CURRENT.set(new Scope(tenant));
        return handle;
    }

    /** Jawny brak placówki: wywołania, które nie dotyczą danych jednej placówki. */
    public static Handle openWithoutTenant() {
        Handle handle = new Handle(CURRENT.get());
        CURRENT.set(new Scope(null));
        return handle;
    }

    public static Optional<TenantId> current() {
        Scope scope = CURRENT.get();
        return scope == null ? Optional.empty() : Optional.ofNullable(scope.tenant());
    }

    public static boolean isExplicitlyWithoutTenant() {
        Scope scope = CURRENT.get();
        return scope != null && scope.tenant() == null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
