package it.govpay.console.gde;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;

import it.govpay.common.client.factory.RestTemplateFactory;
import it.govpay.common.client.oauth2.Oauth2ClientCredentialsManager;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.repository.ConfigurazioneRepository;
import it.govpay.common.repository.ConnettoreEntityRepository;

/**
 * Wiring esplicito e minimo dei bean di sola lettura di govpay-common
 * necessari a {@link ConsoleGdeService} (RestTemplate del connettore GDE,
 * policy del Giornale). Deliberatamente NON via {@code @ComponentScan} su
 * {@code it.govpay.common.client}/{@code it.govpay.common.configurazione}:
 * quei pacchetti contengono classi con lo stesso nome di controparti CRUD
 * gia' presenti in console-api (es. {@code ConnettoreService} — vedi
 * {@link it.govpay.console.connettore.ConnettoreService}). Importando qui
 * solo le classi puntuali che servono, con nomi bean espliciti dove serve
 * disambiguare, il wiring resta immune a qualunque collisione futura senza
 * dover auditare l'intero albero di common ogni volta.
 */
@Configuration
public class GdeCommonBeansConfig {

    @Bean
    public Oauth2ClientCredentialsManager oauth2ClientCredentialsManager() {
        return new Oauth2ClientCredentialsManager();
    }

    @Bean
    public RestTemplateFactory restTemplateFactory(Oauth2ClientCredentialsManager oauth2ClientCredentialsManager,
                                                    ObjectMapper objectMapper) {
        return new RestTemplateFactory(oauth2ClientCredentialsManager, objectMapper);
    }

    @Bean("commonConnettoreService")
    public ConnettoreService commonConnettoreService(ConnettoreEntityRepository connettoreEntityRepository,
                                                       RestTemplateFactory restTemplateFactory,
                                                       @Qualifier("asyncHttpExecutor") Executor asyncHttpExecutor) {
        return new ConnettoreService(connettoreEntityRepository, restTemplateFactory, asyncHttpExecutor);
    }

    @Bean
    public ConfigurazioneService configurazioneService(ConfigurazioneRepository configurazioneRepository,
                                                        ObjectMapper objectMapper,
                                                        @Qualifier("commonConnettoreService") ConnettoreService commonConnettoreService) {
        return new ConfigurazioneService(configurazioneRepository, objectMapper, commonConnettoreService);
    }
}
