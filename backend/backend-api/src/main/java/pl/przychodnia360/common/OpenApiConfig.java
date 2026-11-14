package pl.przychodnia360.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * Stały adres serwera i wersja kontraktu: plik api/openapi.json jest zatwierdzany w repozytorium,
     * więc nie może zależeć od portu ani wersji artefaktu.
     */
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Przychodnia360 API").version("v1")
                        .description("Wersja demonstracyjna, dane wyłącznie syntetyczne."))
                .servers(List.of(new Server().url("/")));
    }
}
