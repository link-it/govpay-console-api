package it.govpay.console.impostazioni;

import org.springframework.stereotype.Component;

import it.govpay.common.configurazione.model.AvvisaturaViaMail;
import it.govpay.common.configurazione.model.PromemoriaAvviso;
import it.govpay.common.configurazione.model.PromemoriaRicevuta;
import it.govpay.common.configurazione.model.PromemoriaScadenza;
import it.govpay.console.model.ImpostazioniMailTemplatePromemoria;
import it.govpay.console.model.TemplatePromemoriaAvviso;
import it.govpay.console.model.TemplatePromemoriaRicevuta;
import it.govpay.console.model.TemplatePromemoriaScadenza;
import it.govpay.console.model.TipoTemplateTrasformazione;

/**
 * Conversione bidirezionale tra {@link ImpostazioniMailTemplatePromemoria}
 * e {@link AvvisaturaViaMail} (bean di {@code govpay-common}, deserializzato
 * dal blob {@code configurazione}, chiave {@code avvisatura_mail}).
 */
@Component
public class MailPromemoriaMapper {

    public ImpostazioniMailTemplatePromemoria toDto(AvvisaturaViaMail source) {
        ImpostazioniMailTemplatePromemoria dto = new ImpostazioniMailTemplatePromemoria();
        dto.setPromemoriaAvviso(toAvviso(source.getPromemoriaAvviso()));
        dto.setPromemoriaRicevuta(toRicevuta(source.getPromemoriaRicevuta()));
        dto.setPromemoriaScadenza(toScadenza(source.getPromemoriaScadenza()));
        return dto;
    }

    public AvvisaturaViaMail toCommon(ImpostazioniMailTemplatePromemoria dto) {
        AvvisaturaViaMail target = new AvvisaturaViaMail();

        TemplatePromemoriaAvviso avviso = dto.getPromemoriaAvviso();
        PromemoriaAvviso avvisoCommon = new PromemoriaAvviso();
        avvisoCommon.setOggetto(avviso != null ? avviso.getOggetto() : null);
        avvisoCommon.setMessaggio(avviso != null ? avviso.getMessaggio() : null);
        avvisoCommon.setAllegaPdf(avviso != null && Boolean.TRUE.equals(avviso.getAllegaPdf()));
        target.setPromemoriaAvviso(avvisoCommon);

        TemplatePromemoriaRicevuta ricevuta = dto.getPromemoriaRicevuta();
        PromemoriaRicevuta ricevutaCommon = new PromemoriaRicevuta();
        ricevutaCommon.setOggetto(ricevuta != null ? ricevuta.getOggetto() : null);
        ricevutaCommon.setMessaggio(ricevuta != null ? ricevuta.getMessaggio() : null);
        ricevutaCommon.setAllegaPdf(ricevuta != null && Boolean.TRUE.equals(ricevuta.getAllegaPdf()));
        ricevutaCommon.setSoloEseguiti(ricevuta != null && Boolean.TRUE.equals(ricevuta.getSoloEseguiti()));
        target.setPromemoriaRicevuta(ricevutaCommon);

        TemplatePromemoriaScadenza scadenza = dto.getPromemoriaScadenza();
        PromemoriaScadenza scadenzaCommon = new PromemoriaScadenza();
        scadenzaCommon.setOggetto(scadenza != null ? scadenza.getOggetto() : null);
        scadenzaCommon.setMessaggio(scadenza != null ? scadenza.getMessaggio() : null);
        scadenzaCommon.setPreavviso(scadenza != null ? scadenza.getPreavviso() : null);
        target.setPromemoriaScadenza(scadenzaCommon);

        return target;
    }

    private static TemplatePromemoriaAvviso toAvviso(PromemoriaAvviso source) {
        TemplatePromemoriaAvviso dto = new TemplatePromemoriaAvviso(TipoTemplateTrasformazione.FREEMARKER);
        if (source != null) {
            dto.setOggetto(source.getOggetto());
            dto.setMessaggio(source.getMessaggio());
            dto.setAllegaPdf(source.isAllegaPdf());
        }
        return dto;
    }

    private static TemplatePromemoriaRicevuta toRicevuta(PromemoriaRicevuta source) {
        TemplatePromemoriaRicevuta dto = new TemplatePromemoriaRicevuta(TipoTemplateTrasformazione.FREEMARKER);
        if (source != null) {
            dto.setOggetto(source.getOggetto());
            dto.setMessaggio(source.getMessaggio());
            dto.setSoloEseguiti(source.isSoloEseguiti());
            dto.setAllegaPdf(source.isAllegaPdf());
        }
        return dto;
    }

    private static TemplatePromemoriaScadenza toScadenza(PromemoriaScadenza source) {
        TemplatePromemoriaScadenza dto = new TemplatePromemoriaScadenza(TipoTemplateTrasformazione.FREEMARKER);
        if (source != null) {
            dto.setOggetto(source.getOggetto());
            dto.setMessaggio(source.getMessaggio());
            dto.setPreavviso(source.getPreavviso());
        }
        return dto;
    }
}
