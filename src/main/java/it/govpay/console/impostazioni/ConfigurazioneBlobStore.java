package it.govpay.console.impostazioni;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import it.govpay.common.entity.ConfigurazioneEntity;
import it.govpay.common.repository.ConfigurazioneRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Lettura/scrittura delle righe della tabella V1 {@code configurazione}
 * (key-value: {@code nome}, {@code valore} JSON), riusata cosi' com'e' invece
 * di introdurre tabelle dedicate per area — un'installazione che aggiorna da
 * V1 trova quindi i propri dati gia' al posto giusto, senza migrazione.
 *
 * <p>Le chiavi ({@code ConfigurazioneKeys}) e i bean di (de)serializzazione
 * (es. {@code Hardening}, {@code MailBatch}) sono quelli di {@code govpay-common},
 * identici campo-per-campo a V1 (incluso il typo storico {@code googleCatpcha}:
 * va preservato, non corretto, per restare compatibili col JSON reale).
 *
 * <p>A differenza di V1 (che su ogni scrittura cancella e reinserisce
 * l'intera tabella — sensato li' perche' V1 espone un solo endpoint per
 * l'intero blob), qui si aggiorna solo la riga della singola area: ogni area
 * ha un proprio endpoint REST indipendente.
 */
@Component
public class ConfigurazioneBlobStore {

    private final ConfigurazioneRepository repository;
    private final ObjectMapper objectMapper;

    public ConfigurazioneBlobStore(ConfigurazioneRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Legge e deserializza la riga {@code nome}; {@code defaultSupplier} se assente o {@code valore} nullo. */
    public <T> T read(String nome, Class<T> type, Supplier<T> defaultSupplier) {
        return repository.findByNome(nome)
                .map(ConfigurazioneEntity::getValore)
                .map(json -> objectMapper.readValue(json, type))
                .orElseGet(defaultSupplier);
    }

    /** Serializza e scrive (upsert per {@code nome}, mai delete-all come in V1). */
    public void write(String nome, Object valore) {
        ConfigurazioneEntity entity = repository.findByNome(nome).orElseGet(() -> {
            ConfigurazioneEntity nuova = new ConfigurazioneEntity();
            nuova.setNome(nome);
            return nuova;
        });
        entity.setValore(objectMapper.writeValueAsString(valore));
        repository.save(entity);
    }
}
