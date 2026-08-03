package it.govpay.console;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;

import it.govpay.common.entity.ConfigurazioneEntity;
import it.govpay.common.entity.ConnettoreEntity;
import it.govpay.common.repository.ApplicazioneRepository;
import it.govpay.common.repository.DominioLogoRepository;
import it.govpay.common.repository.DominioRepository;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.common.repository.StazioneRepository;

/**
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
 * <p>
 * Entity registrate via {@link #persistenceManagedTypes(ResourceLoader)}
 * invece del piu' semplice {@code @EntityScan(basePackages = "it.govpay.common.entity")}:
 * vedi il commento su quel bean per il motivo.
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableJpaRepositories(basePackages = { "it.govpay.console.repository", "it.govpay.common.repository" },
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = { ApplicazioneRepository.class, DominioRepository.class,
                        IntermediarioRepository.class, StazioneRepository.class, DominioLogoRepository.class }))
public class GovPayConsoleApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(GovPayConsoleApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(GovPayConsoleApplication.class, args);
    }

    /**
     * Sostituisce {@code @EntityScan(basePackages = {"it.govpay.console.entity",
     * "it.govpay.common.entity"})}. Un {@code @EntityScan} su tutto
     * {@code it.govpay.common.entity} non e' selettivo: lo scan di un package
     * e' sempre ricorsivo (nessun {@code excludeFilters} disponibile su
     * {@code @EntityScan}, a differenza di {@code @EnableJpaRepositories}
     * sopra) e trascinerebbe dentro anche {@code it.govpay.common.entity.batch}
     * ({@code BatchJobExecutionEntity} e affini) — entity che qui non sono
     * mai lette da nessun repository vivo (nessuna {@code @EnableJpaRepositories}
     * le referenzia), ma che Hibernate pretenderebbe comunque di validare
     * contro lo schema, richiedendo tabelle Spring Batch che console-api
     * non crea e non usa. Idem per le altre entity di common gia' escluse a
     * livello di repository ({@code ApplicazioneEntity}, {@code DominioEntity}, ecc.).
     * <p>
     * {@link PersistenceManagedTypes} e' il meccanismo con cui Spring Boot
     * (dalla 3.x, per l'AOT su native image) disaccoppia "quali classi sono
     * gestite da JPA" dallo scan a runtime: e' una lista di nomi di classe
     * gia' risolti, non un package da percorrere. Se il contesto definisce
     * questo bean, Spring Boot lo usa al posto dello scan automatico.
     * {@link PersistenceManagedTypesScanner} e' la stessa classe usata
     * internamente da {@code @EntityScan}, qui richiamata esplicitamente solo
     * per {@code it.govpay.console.entity} (scan reale, dinamico: le nuove
     * entity proprie di console-api continuano a essere scoperte da sole).
     * Le due entity di common realmente usate (a supporto di
     * {@code ConnettoreEntityRepository}/{@code ConfigurazioneRepository})
     * sono aggiunte per nome, non tramite scan di package: cosi' il
     * sottopacchetto {@code .batch} non ha alcuna via per entrare nella lista.
     */
    @Bean
    public PersistenceManagedTypes persistenceManagedTypes(ResourceLoader resourceLoader) {
        PersistenceManagedTypes proprie = new PersistenceManagedTypesScanner(resourceLoader)
                .scan("it.govpay.console.entity");

        List<String> nomiClassi = new ArrayList<>(proprie.getManagedClassNames());
        nomiClassi.add(ConnettoreEntity.class.getName());
        nomiClassi.add(ConfigurazioneEntity.class.getName());

        return PersistenceManagedTypes.of(nomiClassi, List.of("it.govpay.console.entity"));
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
