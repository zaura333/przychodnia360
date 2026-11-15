package pl.przychodnia360;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SchedulingApplication {

    static {
        // Strefa JVM zawsze UTC; strefa Europe/Warsaw jest sprawą prezentacji (frontend) i reguł domenowych.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(SchedulingApplication.class, args);
    }
}
