package it.govpay.console.impostazioni;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.ImpostazioniMailPromemoria;
import it.govpay.console.model.ImpostazioniMailTemplatePromemoria;
import it.govpay.console.model.TemplatePromemoriaAvviso;
import it.govpay.console.model.TemplatePromemoriaRicevuta;
import it.govpay.console.model.TemplatePromemoriaScadenza;
import it.govpay.console.model.TipoTemplateTrasformazione;

/**
 * Conversione bidirezionale tra {@link ImpostazioniMailTemplatePromemoria}
 * (3 blocchi top-level) e le 3 righe di {@code impostazioni_mail_promemoria},
 * chiave naturale {@code tipo_promemoria}.
 */
@Component
public class MailPromemoriaMapper {

    public ImpostazioniMailTemplatePromemoria toDto(Map<String, ImpostazioniMailPromemoria> byTipo) {
        ImpostazioniMailTemplatePromemoria dto = new ImpostazioniMailTemplatePromemoria();
        dto.setPromemoriaAvviso(toAvviso(byTipo.get(ImpostazioniMailPromemoria.AVVISO)));
        dto.setPromemoriaRicevuta(toRicevuta(byTipo.get(ImpostazioniMailPromemoria.RICEVUTA)));
        dto.setPromemoriaScadenza(toScadenza(byTipo.get(ImpostazioniMailPromemoria.SCADENZA)));
        return dto;
    }

    public Map<String, ImpostazioniMailPromemoria> toEntities(ImpostazioniMailTemplatePromemoria dto) {
        Map<String, ImpostazioniMailPromemoria> map = new LinkedHashMap<>();

        TemplatePromemoriaAvviso avviso = dto.getPromemoriaAvviso();
        ImpostazioniMailPromemoria avvisoEntity = new ImpostazioniMailPromemoria();
        avvisoEntity.setTipoPromemoria(ImpostazioniMailPromemoria.AVVISO);
        avvisoEntity.setOggetto(avviso != null ? avviso.getOggetto() : null);
        avvisoEntity.setMessaggio(avviso != null ? avviso.getMessaggio() : null);
        avvisoEntity.setAllegaPdf(avviso != null ? avviso.getAllegaPdf() : null);
        map.put(ImpostazioniMailPromemoria.AVVISO, avvisoEntity);

        TemplatePromemoriaRicevuta ricevuta = dto.getPromemoriaRicevuta();
        ImpostazioniMailPromemoria ricevutaEntity = new ImpostazioniMailPromemoria();
        ricevutaEntity.setTipoPromemoria(ImpostazioniMailPromemoria.RICEVUTA);
        ricevutaEntity.setOggetto(ricevuta != null ? ricevuta.getOggetto() : null);
        ricevutaEntity.setMessaggio(ricevuta != null ? ricevuta.getMessaggio() : null);
        ricevutaEntity.setAllegaPdf(ricevuta != null ? ricevuta.getAllegaPdf() : null);
        ricevutaEntity.setSoloEseguiti(ricevuta != null ? ricevuta.getSoloEseguiti() : null);
        map.put(ImpostazioniMailPromemoria.RICEVUTA, ricevutaEntity);

        TemplatePromemoriaScadenza scadenza = dto.getPromemoriaScadenza();
        ImpostazioniMailPromemoria scadenzaEntity = new ImpostazioniMailPromemoria();
        scadenzaEntity.setTipoPromemoria(ImpostazioniMailPromemoria.SCADENZA);
        scadenzaEntity.setOggetto(scadenza != null ? scadenza.getOggetto() : null);
        scadenzaEntity.setMessaggio(scadenza != null ? scadenza.getMessaggio() : null);
        scadenzaEntity.setPreavviso(scadenza != null ? scadenza.getPreavviso() : null);
        map.put(ImpostazioniMailPromemoria.SCADENZA, scadenzaEntity);

        return map;
    }

    private static TemplatePromemoriaAvviso toAvviso(ImpostazioniMailPromemoria entity) {
        TemplatePromemoriaAvviso dto = new TemplatePromemoriaAvviso(TipoTemplateTrasformazione.FREEMARKER);
        if (entity != null) {
            dto.setOggetto(entity.getOggetto());
            dto.setMessaggio(entity.getMessaggio());
            dto.setAllegaPdf(entity.getAllegaPdf());
        }
        return dto;
    }

    private static TemplatePromemoriaRicevuta toRicevuta(ImpostazioniMailPromemoria entity) {
        TemplatePromemoriaRicevuta dto = new TemplatePromemoriaRicevuta(TipoTemplateTrasformazione.FREEMARKER);
        if (entity != null) {
            dto.setOggetto(entity.getOggetto());
            dto.setMessaggio(entity.getMessaggio());
            dto.setSoloEseguiti(entity.getSoloEseguiti());
            dto.setAllegaPdf(entity.getAllegaPdf());
        }
        return dto;
    }

    private static TemplatePromemoriaScadenza toScadenza(ImpostazioniMailPromemoria entity) {
        TemplatePromemoriaScadenza dto = new TemplatePromemoriaScadenza(TipoTemplateTrasformazione.FREEMARKER);
        if (entity != null) {
            dto.setOggetto(entity.getOggetto());
            dto.setMessaggio(entity.getMessaggio());
            dto.setPreavviso(entity.getPreavviso());
        }
        return dto;
    }
}
