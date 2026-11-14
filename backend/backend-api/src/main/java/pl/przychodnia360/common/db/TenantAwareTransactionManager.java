package pl.przychodnia360.common.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;

/**
 * Wiąże {@link TenantContext} z transakcją: na początku każdej transakcji wykonuje
 * {@code set_config('app.tenant_id', ?, true)} (wartość lokalna dla transakcji, więc znika przy commit/rollback
 * i nie przecieka między żądaniami na tym samym połączeniu z puli).
 *
 * <p>Transakcja bez kontekstu i bez jawnego {@link TenantContext#openWithoutTenant()} jest odrzucana, zanim
 * pobierze połączenie. Zapytania poza transakcją (autocommit) nie mają kontekstu, więc RLS nie zwraca wierszy.
 */
public class TenantAwareTransactionManager extends DataSourceTransactionManager {

    private static final String SET_TENANT_SQL = "SELECT set_config('app.tenant_id', ?, true)";

    public TenantAwareTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        TenantId tenant = TenantContext.current().orElse(null);
        if (tenant == null && !TenantContext.isExplicitlyWithoutTenant()) {
            throw new MissingTenantContextException();
        }
        super.doBegin(transaction, definition);
        if (tenant == null) {
            return;
        }
        try {
            ConnectionHolder holder =
                    (ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource());
            Connection connection = holder.getConnection();
            try (PreparedStatement ps = connection.prepareStatement(SET_TENANT_SQL)) {
                ps.setString(1, tenant.toString());
                ps.execute();
            }
        } catch (SQLException | RuntimeException e) {
            doCleanupAfterCompletion(transaction);
            throw new CannotCreateTransactionException("Nie udało się ustawić kontekstu placówki", e);
        }
    }

    /** Transakcja wymagająca placówki została otwarta bez kontekstu. */
    public static class MissingTenantContextException extends CannotCreateTransactionException {
        public MissingTenantContextException() {
            super("Transakcja bez kontekstu placówki (użyj TenantContext.open lub openWithoutTenant)");
        }
    }
}
