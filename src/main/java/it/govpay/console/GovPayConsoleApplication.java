package it.govpay.console;

import java.time.Clock;
import java.util.TimeZone;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import it.govpay.common.repository.ApplicazioneRepository;
import it.govpay.common.repository.DominioLogoRepository;
import it.govpay.common.repository.DominioRepository;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.common.repository.StazioneRepository;

/**
 * Entity scan esteso a {@code it.govpay.common.entity} (nessun conflitto:
 * common usa il suffisso "Entity" proprio per evitare collisioni di nome con
 * le entity slim di console-api, es. {@code ConnettoreEntity} vs
 * {@code ConnettoreProprieta}).
 * <p>
 * Repository scan esteso a {@code it.govpay.common.repository} ma con
 * {@code excludeFilters}: molte tabelle sono modellate in common in sola
 * lettura (per costruire RestTemplate/leggere configurazione) E in
 * console-api in CRUD completo (issue #6/#7/#8/#24) con la propria entity
 * slim — i repository di common per quelle tabelle avrebbero lo stesso nome
 * bean di default delle controparti CRUD di console. Restano attivi solo
 * {@code ConnettoreEntityRepository} e {@code ConfigurazioneRepository}
 * (nessuna collisione, non hanno un equivalente CRUD in console-api).
 * <p>
 * Component scan NON esteso a {@code it.govpay.common.client}/
 * {@code it.govpay.common.configurazione}: i pochi bean di sola lettura che
 * servono ({@code ConnettoreService}, {@code ConfigurazioneService}, ecc.)
 * sono cablati esplicitamente in {@link it.govpay.console.gde.GdeCommonBeansConfig}
 * con nomi disambiguati, per non dipendere da una scansione di pacchetto che
 * potrebbe portare altre collisioni non ancora note.
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableJpaRepositories(basePackages = { "it.govpay.console.repository", "it.govpay.common.repository" },
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = { ApplicazioneRepository.class, DominioRepository.class,
                        IntermediarioRepository.class, StazioneRepository.class, DominioLogoRepository.class }))
@EntityScan(basePackages = { "it.govpay.console.entity", "it.govpay.common.entity" })
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
    public JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.defaultTimeZone(TimeZone.getTimeZone(this.timeZone));
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
