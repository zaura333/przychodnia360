package pl.przychodnia360.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.przychodnia360.identity.application.PlatformAccountService;

/**
 * Jednorazowe uruchomienie z profilem {@code bootstrap-admin}: zakłada pierwszego administratora platformy ze
 * zmiennych {@code BOOTSTRAP_ADMIN_EMAIL} i {@code BOOTSTRAP_ADMIN_PASSWORD} i kończy proces. Hasło nie trafia do
 * migracji ani do repozytorium (plan 1a, sekcja 9).
 */
@Component
@Profile("bootstrap-admin")
class BootstrapAdminRunner implements ApplicationRunner {

    private final PlatformAccountService accounts;
    private final ConfigurableApplicationContext context;
    private final String email;
    private final String password;

    BootstrapAdminRunner(PlatformAccountService accounts, ConfigurableApplicationContext context,
            @Value("${BOOTSTRAP_ADMIN_EMAIL}") String email, @Value("${BOOTSTRAP_ADMIN_PASSWORD}") String password) {
        this.accounts = accounts;
        this.context = context;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        accounts.ensurePlatformAdmin(email, password);
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
