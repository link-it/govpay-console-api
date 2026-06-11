package it.govpay.console;

import java.time.Clock;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableJpaRepositories(basePackages = "it.govpay.console.repository")
public class GovPayConsoleApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(GovPayConsoleApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(GovPayConsoleApplication.class, args);
    }

    @Value("${console.time-zone:Europe/Rome}")
    String timeZone;

    /**
     * Impostazione del timezone nel mapper Jackson
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.timeZone(this.timeZone);
    }

    /**
     * Registra il modulo per serializzare {@code JsonNullable} (generato dagli
     * schemi OpenAPI con `nullable: true`) in modo trasparente: i campi
     * "undefined" sono omessi, i campi {@code of(null)} sono serializzati come
     * null espliciti, i campi {@code of(value)} come il loro valore.
     */
    @Bean
    public JsonNullableModule jsonNullableModule() {
        return new JsonNullableModule();
    }

    /**
     * Clock di sistema, iniettato dove serve "now" (es. derivazione `SCADUTA`
     * nello stato pendenza). Sostituibile nei test per fissare il tempo.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

}
