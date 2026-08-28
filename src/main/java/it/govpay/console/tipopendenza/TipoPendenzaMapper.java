package it.govpay.console.tipopendenza;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.model.TipoPendenza;
import it.govpay.console.model.TipoPendenzaAvvisaturaAppIo;
import it.govpay.console.model.TipoPendenzaPromemoriaAvvisoAppIo;
import it.govpay.console.model.TipoPendenzaPromemoriaRicevutaAppIo;
import it.govpay.console.model.TipoPendenzaSummary;

/**
 * Mappa l'entity {@link TipoVersamento} (tabella flat con ~55 colonne) verso e
 * dalle proiezioni V2 annidate. La parte di configurazione condivisa con i tipi
 * pendenza di dominio e' delegata a {@link TipoVersamentoConfigMapper}; qui
 * restano l'assemblaggio del dettaglio globale e l'avvisatura App IO (variante
 * globale, senza {@code apiKey}).
 */
@Component
public class TipoPendenzaMapper {

    private final TipoVersamentoConfigMapper config;

    public TipoPendenzaMapper(TipoVersamentoConfigMapper config) {
        this.config = config;
    }

    public TipoPendenzaSummary toSummary(TipoVersamento e) {
        TipoPendenzaSummary dto = new TipoPendenzaSummary();
        dto.setIdTipoPendenza(e.getCodTipoVersamento());
        dto.setDescrizione(e.getDescrizione());
        dto.setAbilitato(e.getAbilitato());
        return dto;
    }

    public TipoPendenza toDetail(TipoVersamento e) {
        TipoPendenza dto = new TipoPendenza();
        dto.setIdTipoPendenza(e.getCodTipoVersamento());
        dto.setDescrizione(e.getDescrizione());
        dto.setCodificaIUV(e.getCodificaIuv());
        dto.setPagaTerzi(e.getPagaTerzi());
        dto.setAbilitato(e.getAbilitato());
        dto.setPortaleBackoffice(config.buildPortaleBackoffice(e));
        dto.setPortalePagamento(config.buildPortalePagamento(e));
        dto.setAvvisaturaMail(config.buildAvvisaturaMail(e));
        dto.setAvvisaturaAppIO(buildAvvisaturaAppIo(e));
        dto.setVisualizzazione(config.textToObj(e.getVisualizzazioneDefinizione()));
        dto.setTracciatoCsv(config.buildTracciatoCsv(e));
        return dto;
    }

    private TipoPendenzaAvvisaturaAppIo buildAvvisaturaAppIo(TipoVersamento e) {
        TipoPendenzaAvvisaturaAppIo appio = new TipoPendenzaAvvisaturaAppIo();

        TipoPendenzaPromemoriaAvvisoAppIo avv = new TipoPendenzaPromemoriaAvvisoAppIo();
        avv.setAbilitato(Boolean.TRUE.equals(e.getAvvAppIoPromAvvAbilitato()));
        avv.setTipo(config.textToEnum(e.getAvvAppIoPromAvvTipo()));
        avv.setOggetto(config.textToObj(e.getAvvAppIoPromAvvOggetto()));
        avv.setMessaggio(config.textToObj(e.getAvvAppIoPromAvvMessaggio()));
        appio.setPromemoriaAvviso(avv);

        TipoPendenzaPromemoriaRicevutaAppIo ric = new TipoPendenzaPromemoriaRicevutaAppIo();
        ric.setAbilitato(Boolean.TRUE.equals(e.getAvvAppIoPromRicAbilitato()));
        ric.setTipo(config.textToEnum(e.getAvvAppIoPromRicTipo()));
        ric.setOggetto(config.textToObj(e.getAvvAppIoPromRicOggetto()));
        ric.setMessaggio(config.textToObj(e.getAvvAppIoPromRicMessaggio()));
        ric.setSoloEseguiti(e.getAvvAppIoPromRicEseguiti());
        appio.setPromemoriaRicevuta(ric);

        appio.setPromemoriaScadenza(config.buildScadenza(
                e.getAvvAppIoPromScadAbilitato(), e.getAvvAppIoPromScadTipo(),
                e.getAvvAppIoPromScadOggetto(), e.getAvvAppIoPromScadMessaggio(),
                e.getAvvAppIoPromScadPreavviso()));
        return appio;
    }

    /**
     * Scrive la parte modificabile sull'entity. Le colonne NOT NULL della
     * tabella ({@code paga_terzi, abilitato, *_abilitato}) ricevono sempre un
     * valore: i flag annidati assenti diventano {@code false}.
     */
    public void applyWritable(TipoVersamento e, DatiTipoPendenza d) {
        e.setDescrizione(d.descrizione());
        e.setCodificaIuv(d.codificaIUV());
        e.setPagaTerzi(d.pagaTerzi() != null ? d.pagaTerzi() : Boolean.FALSE);
        e.setAbilitato(d.abilitato() != null ? d.abilitato() : Boolean.TRUE);
        e.setVisualizzazioneDefinizione(config.objToText(d.visualizzazione()));

        config.writePortaleBackoffice(e, d.portaleBackoffice());
        config.writePortalePagamento(e, d.portalePagamento());
        config.writeAvvisaturaMail(e, d.avvisaturaMail());
        writeAvvisaturaAppIo(e, d.avvisaturaAppIo());
        config.writeTracciatoCsv(e, d.tracciatoCsv());
    }

    private void writeAvvisaturaAppIo(TipoVersamento e, TipoPendenzaAvvisaturaAppIo appio) {
        config.writeAvvisaturaAppIo(e,
                appio != null ? appio.getPromemoriaAvviso() : null,
                appio != null ? appio.getPromemoriaRicevuta() : null,
                appio != null ? appio.getPromemoriaScadenza() : null);
    }
}
