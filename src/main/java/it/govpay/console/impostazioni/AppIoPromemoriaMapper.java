package it.govpay.console.impostazioni;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.ImpostazioniAppIoPromemoria;
import it.govpay.console.model.ImpostazioniAppIoTemplatePromemoria;
import it.govpay.console.model.TemplatePromemoriaAvvisoBase;
import it.govpay.console.model.TemplatePromemoriaRicevutaBase;
import it.govpay.console.model.TemplatePromemoriaScadenza;
import it.govpay.console.model.TipoTemplateTrasformazione;

/**
 * Conversione bidirezionale tra {@link ImpostazioniAppIoTemplatePromemoria}
 * (3 blocchi top-level) e le 3 righe di {@code impostazioni_appio_promemoria},
 * chiave naturale {@code tipo_promemoria}. Stesso pattern di
 * {@link MailPromemoriaMapper}, senza il campo {@code allegaPdf} (non
 * applicabile alle notifiche push).
 */
@Component
public class AppIoPromemoriaMapper {

    public ImpostazioniAppIoTemplatePromemoria toDto(Map<String, ImpostazioniAppIoPromemoria> byTipo) {
        ImpostazioniAppIoTemplatePromemoria dto = new ImpostazioniAppIoTemplatePromemoria();
        dto.setPromemoriaAvviso(toAvviso(byTipo.get(ImpostazioniAppIoPromemoria.AVVISO)));
        dto.setPromemoriaRicevuta(toRicevuta(byTipo.get(ImpostazioniAppIoPromemoria.RICEVUTA)));
        dto.setPromemoriaScadenza(toScadenza(byTipo.get(ImpostazioniAppIoPromemoria.SCADENZA)));
        return dto;
    }

    public Map<String, ImpostazioniAppIoPromemoria> toEntities(ImpostazioniAppIoTemplatePromemoria dto) {
        Map<String, ImpostazioniAppIoPromemoria> map = new LinkedHashMap<>();

        TemplatePromemoriaAvvisoBase avviso = dto.getPromemoriaAvviso();
        ImpostazioniAppIoPromemoria avvisoEntity = new ImpostazioniAppIoPromemoria();
        avvisoEntity.setTipoPromemoria(ImpostazioniAppIoPromemoria.AVVISO);
        avvisoEntity.setOggetto(avviso != null ? avviso.getOggetto() : null);
        avvisoEntity.setMessaggio(avviso != null ? avviso.getMessaggio() : null);
        map.put(ImpostazioniAppIoPromemoria.AVVISO, avvisoEntity);

        TemplatePromemoriaRicevutaBase ricevuta = dto.getPromemoriaRicevuta();
        ImpostazioniAppIoPromemoria ricevutaEntity = new ImpostazioniAppIoPromemoria();
        ricevutaEntity.setTipoPromemoria(ImpostazioniAppIoPromemoria.RICEVUTA);
        ricevutaEntity.setOggetto(ricevuta != null ? ricevuta.getOggetto() : null);
        ricevutaEntity.setMessaggio(ricevuta != null ? ricevuta.getMessaggio() : null);
        ricevutaEntity.setSoloEseguiti(ricevuta != null ? ricevuta.getSoloEseguiti() : null);
        map.put(ImpostazioniAppIoPromemoria.RICEVUTA, ricevutaEntity);

        TemplatePromemoriaScadenza scadenza = dto.getPromemoriaScadenza();
        ImpostazioniAppIoPromemoria scadenzaEntity = new ImpostazioniAppIoPromemoria();
        scadenzaEntity.setTipoPromemoria(ImpostazioniAppIoPromemoria.SCADENZA);
        scadenzaEntity.setOggetto(scadenza != null ? scadenza.getOggetto() : null);
        scadenzaEntity.setMessaggio(scadenza != null ? scadenza.getMessaggio() : null);
        scadenzaEntity.setPreavviso(scadenza != null ? scadenza.getPreavviso() : null);
        map.put(ImpostazioniAppIoPromemoria.SCADENZA, scadenzaEntity);

        return map;
    }

    private static TemplatePromemoriaAvvisoBase toAvviso(ImpostazioniAppIoPromemoria entity) {
        TemplatePromemoriaAvvisoBase dto = new TemplatePromemoriaAvvisoBase(TipoTemplateTrasformazione.FREEMARKER);
        if (entity != null) {
            dto.setOggetto(entity.getOggetto());
            dto.setMessaggio(entity.getMessaggio());
        }
        return dto;
    }

    private static TemplatePromemoriaRicevutaBase toRicevuta(ImpostazioniAppIoPromemoria entity) {
        TemplatePromemoriaRicevutaBase dto = new TemplatePromemoriaRicevutaBase(TipoTemplateTrasformazione.FREEMARKER);
        if (entity != null) {
            dto.setOggetto(entity.getOggetto());
            dto.setMessaggio(entity.getMessaggio());
            dto.setSoloEseguiti(entity.getSoloEseguiti());
        }
        return dto;
    }

    private static TemplatePromemoriaScadenza toScadenza(ImpostazioniAppIoPromemoria entity) {
        TemplatePromemoriaScadenza dto = new TemplatePromemoriaScadenza(TipoTemplateTrasformazione.FREEMARKER);
        if (entity != null) {
            dto.setOggetto(entity.getOggetto());
            dto.setMessaggio(entity.getMessaggio());
            dto.setPreavviso(entity.getPreavviso());
        }
        return dto;
    }
}
