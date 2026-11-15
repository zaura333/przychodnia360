package pl.przychodnia360;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** iam-service (S3): placówki, konta, logowanie, tokeny dostępu i usługowe, JWKS. */
@SpringBootApplication
public class IamApplication {

    static {
        // Strefa JVM zawsze UTC; strefa Europe/Warsaw jest sprawą prezentacji (frontend) i reguł domenowych.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
