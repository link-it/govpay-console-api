package it.govpay.console.operazioni;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import it.govpay.common.batch.dto.BatchInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.model.Operazione;

@Service
public class OperazioniService {

    private static final Logger log = LoggerFactory.getLogger(OperazioniService.class);

    private final OperazioniProperties operazioniProperties;
    private final OperazioneBatchClient client;
    private final OperazioneMapper mapper;
    private final Map<String, OperazioneLocaleHandler> handlersPerId;

    public OperazioniService(OperazioniProperties operazioniProperties, OperazioneBatchClient client,
            OperazioneMapper mapper, List<OperazioneLocaleHandler> handlers) {
        this.operazioniProperties = operazioniProperties;
        this.client = client;
        this.mapper = mapper;
        this.handlersPerId = handlers.stream().collect(Collectors.toMap(OperazioneLocaleHandler::getId, Function.identity()));
    }

    public List<Operazione> getCatalogo() {
        return operazioniProperties.getCatalogo().stream()
                .map(this::toOperazione)
                .filter(Objects::nonNull)
                .toList();
    }

    private Operazione toOperazione(OperazioneConfig config) {
        if (config.getUrl() == null) {
            OperazioneLocaleHandler handler = handlersPerId.get(config.getId());
            if (handler == null) {
                log.warn("Operazione '{}' non ha ne' un 'url' configurato ne' un OperazioneLocaleHandler registrato: esclusa dal catalogo.", config.getId());
                return null;
            }
            return mapper.toOperazioneLocale(config, handler);
        }
        try {
            BatchInfo info = client.info(config.getUrl());
            LastExecutionInfo ultima = client.lastExecution(config.getUrl());
            NextExecutionInfo prossima = client.nextExecution(config.getUrl());
            return mapper.toOperazione(config, info, ultima, prossima);
        } catch (OperazioneTriggerNonRaggiungibileException e) {
            // Un batch temporaneamente giu' non deve nascondere anche le
            // altre operazioni: la voce resta in lista, degradata.
            log.warn("Microservizio non raggiungibile per l'operazione '{}': {}", config.getId(), e.getMessage());
            return mapper.toOperazioneNonRaggiungibile(config);
        }
    }
}
