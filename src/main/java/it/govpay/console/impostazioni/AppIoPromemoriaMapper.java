package it.govpay.console.impostazioni;

import org.springframework.stereotype.Component;

import it.govpay.common.configurazione.model.AvvisaturaViaAppIo;
import it.govpay.common.configurazione.model.PromemoriaAvvisoBase;
import it.govpay.common.configurazione.model.PromemoriaRicevutaBase;
import it.govpay.common.configurazione.model.PromemoriaScadenza;
import it.govpay.console.model.ImpostazioniAppIoTemplatePromemoria;
import it.govpay.console.model.TemplatePromemoriaAvvisoBase;
import it.govpay.console.model.TemplatePromemoriaRicevutaBase;
import it.govpay.console.model.TemplatePromemoriaScadenza;
import it.govpay.console.model.TipoTemplateTrasformazione;

/**
 * Conversione bidirezionale tra {@link ImpostazioniAppIoTemplatePromemoria}
 * e {@link AvvisaturaViaAppIo} (bean di {@code govpay-common}, deserializzato
 * dal blob {@code configurazione}, chiave {@code avvisatura_app_io}). Stesso
 * pattern di {@link MailPromemoriaMapper}, senza il campo {@code allegaPdf}
 * (non applicabile alle notifiche push).
 */
@Component
public class AppIoPromemoriaMapper {

    public ImpostazioniAppIoTemplatePromemoria toDto(AvvisaturaViaAppIo source) {
        ImpostazioniAppIoTemplatePromemoria dto = new ImpostazioniAppIoTemplatePromemoria();
        dto.setPromemoriaAvviso(toAvviso(source.getPromemoriaAvviso()));
        dto.setPromemoriaRicevuta(toRicevuta(source.getPromemoriaRicevuta()));
        dto.setPromemoriaScadenza(toScadenza(source.getPromemoriaScadenza()));
        return dto;
    }

    public AvvisaturaViaAppIo toCommon(ImpostazioniAppIoTemplatePromemoria dto) {
        AvvisaturaViaAppIo target = new AvvisaturaViaAppIo();

        TemplatePromemoriaAvvisoBase avviso = dto.getPromemoriaAvviso();
        PromemoriaAvvisoBase avvisoCommon = new PromemoriaAvvisoBase();
        avvisoCommon.setOggetto(avviso != null ? avviso.getOggetto() : null);
        avvisoCommon.setMessaggio(avviso != null ? avviso.getMessaggio() : null);
        target.setPromemoriaAvviso(avvisoCommon);

        TemplatePromemoriaRicevutaBase ricevuta = dto.getPromemoriaRicevuta();
        PromemoriaRicevutaBase ricevutaCommon = new PromemoriaRicevutaBase();
        ricevutaCommon.setOggetto(ricevuta != null ? ricevuta.getOggetto() : null);
        ricevutaCommon.setMessaggio(ricevuta != null ? ricevuta.getMessaggio() : null);
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

    private static TemplatePromemoriaAvvisoBase toAvviso(PromemoriaAvvisoBase source) {
        TemplatePromemoriaAvvisoBase dto = new TemplatePromemoriaAvvisoBase(TipoTemplateTrasformazione.FREEMARKER);
        if (source != null) {
            dto.setOggetto(source.getOggetto());
            dto.setMessaggio(source.getMessaggio());
        }
        return dto;
    }

    private static TemplatePromemoriaRicevutaBase toRicevuta(PromemoriaRicevutaBase source) {
        TemplatePromemoriaRicevutaBase dto = new TemplatePromemoriaRicevutaBase(TipoTemplateTrasformazione.FREEMARKER);
        if (source != null) {
            dto.setOggetto(source.getOggetto());
            dto.setMessaggio(source.getMessaggio());
            dto.setSoloEseguiti(source.isSoloEseguiti());
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
