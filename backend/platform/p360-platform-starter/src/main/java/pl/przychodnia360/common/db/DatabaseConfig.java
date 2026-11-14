package pl.przychodnia360.common.db;

import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DatabaseConfig {

    /** Zastępuje domyślny menedżer transakcji Spring Boot (jego autokonfiguracja ustępuje własnemu beanowi). */
    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new TenantAwareTransactionManager(dataSource);
    }

    /** Zegar jako bean, żeby reguły czasowe (np. odwołania 24 h) były testowalne. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
