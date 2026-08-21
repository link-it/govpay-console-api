package it.govpay.console.tracciato;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.openapitools.jackson.nullable.JsonNullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Tracciato;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.FormatoTracciato;
import it.govpay.console.model.Link;
import it.govpay.console.model.TracciatoPendenze;
import it.govpay.console.model.TracciatoPendenzeLinks;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Mappa {@link Tracciato} (entity slim su tabella V1 {@code tracciati}) sullo
 * schema di output V2 {@link TracciatoPendenze}, deserializzando al volo
 * {@code bean_dati} per stato fine-grain e contatori (vedi {@link TracciatoBeanDati}).
 */
@Component
public class TracciatoMapper {

    private static final Logger log = LoggerFactory.getLogger(TracciatoMapper.class);

    private static final DateTimeFormatter BEAN_DATI_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final ObjectMapper objectMapper;

    public TracciatoMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TracciatoPendenze toDto(Tracciato entity) {
        TracciatoBeanDati beanDati = parseBeanDati(entity);

        TracciatoPendenze dto = new TracciatoPendenze(
                entity.getId(),
                entity.getFileNameRichiesta(),
                toDominioRef(entity.getDominio()),
                entity.getDataCaricamento(),
                TracciatoStatoMapper.toRest(entity.getStato(), beanDati.getStepElaborazione()),
                FormatoTracciato.fromValue(entity.getFormato()));

        dto.setIdTipoPendenza(JsonNullable.of(entity.getCodTipoVersamento()));
        dto.setDataOraUltimoAggiornamento(JsonNullable.of(parseDataUltimoAggiornamento(beanDati)));
        dto.setDescrizioneStato(JsonNullable.of(beanDati.getDescrizioneStepElaborazione()));

        dto.setNumeroOperazioniTotali(beanDati.getNumAddTotali() + beanDati.getNumDelTotali());
        dto.setNumeroOperazioniEseguite(beanDati.getNumAddOk() + beanDati.getNumDelOk());
        dto.setNumeroOperazioniFallite(beanDati.getNumAddKo() + beanDati.getNumDelKo());

        if (beanDati.isStampaAvvisi()) {
            dto.setNumeroAvvisiTotali(JsonNullable.of(beanDati.getNumStampeTotali()));
            dto.setNumeroAvvisiStampati(JsonNullable.of(beanDati.getNumStampeOk()));
            dto.setNumeroAvvisiFalliti(JsonNullable.of(beanDati.getNumStampeKo()));
        }
        dto.setStampaAvvisi(beanDati.isStampaAvvisi());

        Operatore operatore = entity.getOperatore();
        if (operatore != null && operatore.getUtenza() != null) {
            dto.setOperatoreMittente(operatore.getUtenza().getPrincipal());
        }

        dto.setLinks(new TracciatoPendenzeLinks().self(new Link("/pendenze/tracciati/" + entity.getId())));
        return dto;
    }

    private TracciatoBeanDati parseBeanDati(Tracciato entity) {
        String json = entity.getBeanDati();
        if (json == null || json.isBlank()) {
            return new TracciatoBeanDati();
        }
        try {
            return objectMapper.readValue(json, TracciatoBeanDati.class);
        } catch (JacksonException e) {
            log.warn("bean_dati non deserializzabile per tracciato id={}, uso valori di default", entity.getId(), e);
            return new TracciatoBeanDati();
        }
    }

    private static OffsetDateTime parseDataUltimoAggiornamento(TracciatoBeanDati beanDati) {
        String raw = beanDati.getDataUltimoAggiornamento();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, BEAN_DATI_DATE_FORMAT).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            log.warn("bean_dati.dataUltimoAggiornamento non parsabile: '{}'", raw, e);
            return null;
        }
    }

    private static DominioRef toDominioRef(Dominio dominio) {
        DominioRef ref = new DominioRef(dominio.getCodDominio());
        ref.setRagioneSociale(dominio.getRagioneSociale());
        return ref;
    }
}
