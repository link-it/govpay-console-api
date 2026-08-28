package it.govpay.console.tipopendenzadominio;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.model.TipoPendenzaDominio;
import it.govpay.console.model.TipoPendenzaDominioAvvisaturaAppIo;
import it.govpay.console.model.TipoPendenzaDominioSummary;
import it.govpay.console.model.TipoPendenzaPromemoriaAvvisoAppIo;
import it.govpay.console.model.TipoPendenzaPromemoriaRicevutaAppIo;
import it.govpay.console.tipopendenza.TipoPendenzaMapper;
import it.govpay.console.tipopendenza.TipoVersamentoConfigMapper;

/**
 * Mappa l'entity {@link TipoVersamentoDominio} (configurazione per-dominio) verso
 * e dalle proiezioni V2. La parte di configurazione condivisa col globale e'
 * delegata a {@link TipoVersamentoConfigMapper}; l'avvisatura App IO ha la
 * variante di dominio (con {@code apiKey}). Il riferimento al tipo pendenza
 * globale e' costruito con {@link TipoPendenzaMapper}.
 */
@Component
public class TipoPendenzaDominioMapper {

    private final TipoVersamentoConfigMapper config;
    private final TipoPendenzaMapper tipoPendenzaMapper;

    public TipoPendenzaDominioMapper(TipoVersamentoConfigMapper config,
                                     TipoPendenzaMapper tipoPendenzaMapper) {
        this.config = config;
        this.tipoPendenzaMapper = tipoPendenzaMapper;
    }

    public TipoPendenzaDominioSummary toSummary(TipoVersamentoDominio e) {
        TipoPendenzaDominioSummary dto = new TipoPendenzaDominioSummary();
        dto.setIdTipoPendenza(e.getTipoVersamento().getCodTipoVersamento());
        dto.setDescrizione(e.getTipoVersamento().getDescrizione());
        dto.setAbilitato(e.getAbilitato());
        return dto;
    }

    public TipoPendenzaDominio toDetail(TipoVersamentoDominio e) {
        TipoPendenzaDominio dto = new TipoPendenzaDominio();
        dto.setIdTipoPendenza(e.getTipoVersamento().getCodTipoVersamento());
        dto.setCodificaIUV(e.getCodificaIuv());
        dto.setPagaTerzi(e.getPagaTerzi());
        dto.setAbilitato(e.getAbilitato());
        dto.setPortaleBackoffice(config.buildPortaleBackoffice(e));
        dto.setPortalePagamento(config.buildPortalePagamento(e));
        dto.setAvvisaturaMail(config.buildAvvisaturaMail(e));
        dto.setAvvisaturaAppIO(buildAvvisaturaAppIo(e));
        dto.setVisualizzazione(config.textToObj(e.getVisualizzazioneDefinizione()));
        dto.setTracciatoCsv(config.buildTracciatoCsv(e));
        dto.setTipoPendenza(tipoPendenzaMapper.toDetail(e.getTipoVersamento()));
        return dto;
    }

    private TipoPendenzaDominioAvvisaturaAppIo buildAvvisaturaAppIo(TipoVersamentoDominio e) {
        TipoPendenzaDominioAvvisaturaAppIo appio = new TipoPendenzaDominioAvvisaturaAppIo();

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
        appio.setApiKey(e.getAppIoApiKey());
        return appio;
    }

    /** Scrive la parte modificabile (no {@code descrizione}: vive sul globale). */
    public void applyWritable(TipoVersamentoDominio e, DatiTipoPendenzaDominio d) {
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

    private void writeAvvisaturaAppIo(TipoVersamentoDominio e, TipoPendenzaDominioAvvisaturaAppIo appio) {
        config.writeAvvisaturaAppIo(e,
                appio != null ? appio.getPromemoriaAvviso() : null,
                appio != null ? appio.getPromemoriaRicevuta() : null,
                appio != null ? appio.getPromemoriaScadenza() : null);
        // Solo la variante dominio porta l'apiKey: il resto e' identico al tipo pendenza globale.
        e.setAppIoApiKey(appio != null ? appio.getApiKey() : null);
    }
}
