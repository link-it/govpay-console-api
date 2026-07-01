package it.govpay.console.tipopendenzadominio;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.model.TipoPendenzaAvvisaturaMail;
import it.govpay.console.model.TipoPendenzaDominio;
import it.govpay.console.model.TipoPendenzaDominioAvvisaturaAppIo;
import it.govpay.console.model.TipoPendenzaDominioSummary;
import it.govpay.console.model.TipoPendenzaPortaleBackoffice;
import it.govpay.console.model.TipoPendenzaPortalePagamento;
import it.govpay.console.model.TipoPendenzaPromemoriaAvvisoAppIo;
import it.govpay.console.model.TipoPendenzaPromemoriaRicevutaAppIo;
import it.govpay.console.model.TipoPendenzaPromemoriaScadenza;
import it.govpay.console.model.TipoPendenzaTracciatoCsv;
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
    public void applyWritable(TipoVersamentoDominio e,
            String codificaIUV,
            Boolean pagaTerzi,
            Boolean abilitato,
            TipoPendenzaPortaleBackoffice bo,
            TipoPendenzaPortalePagamento pag,
            TipoPendenzaAvvisaturaMail mail,
            TipoPendenzaDominioAvvisaturaAppIo appio,
            Object visualizzazione,
            TipoPendenzaTracciatoCsv csv) {

        e.setCodificaIuv(codificaIUV);
        e.setPagaTerzi(pagaTerzi != null ? pagaTerzi : Boolean.FALSE);
        e.setAbilitato(abilitato != null ? abilitato : Boolean.TRUE);
        e.setVisualizzazioneDefinizione(config.objToText(visualizzazione));

        config.writePortaleBackoffice(e, bo);
        config.writePortalePagamento(e, pag);
        config.writeAvvisaturaMail(e, mail);
        writeAvvisaturaAppIo(e, appio);
        config.writeTracciatoCsv(e, csv);
    }

    private void writeAvvisaturaAppIo(TipoVersamentoDominio e, TipoPendenzaDominioAvvisaturaAppIo appio) {
        TipoPendenzaPromemoriaAvvisoAppIo avv = appio != null ? appio.getPromemoriaAvviso() : null;
        e.setAvvAppIoPromAvvAbilitato(avv != null && Boolean.TRUE.equals(avv.getAbilitato()));
        e.setAvvAppIoPromAvvTipo(avv != null ? config.enumToText(avv.getTipo()) : null);
        e.setAvvAppIoPromAvvOggetto(avv != null ? config.objToText(avv.getOggetto()) : null);
        e.setAvvAppIoPromAvvMessaggio(avv != null ? config.objToText(avv.getMessaggio()) : null);

        TipoPendenzaPromemoriaRicevutaAppIo ric = appio != null ? appio.getPromemoriaRicevuta() : null;
        e.setAvvAppIoPromRicAbilitato(ric != null && Boolean.TRUE.equals(ric.getAbilitato()));
        e.setAvvAppIoPromRicTipo(ric != null ? config.enumToText(ric.getTipo()) : null);
        e.setAvvAppIoPromRicOggetto(ric != null ? config.objToText(ric.getOggetto()) : null);
        e.setAvvAppIoPromRicMessaggio(ric != null ? config.objToText(ric.getMessaggio()) : null);
        e.setAvvAppIoPromRicEseguiti(ric != null ? ric.getSoloEseguiti() : null);

        TipoPendenzaPromemoriaScadenza scad = appio != null ? appio.getPromemoriaScadenza() : null;
        e.setAvvAppIoPromScadAbilitato(scad != null && Boolean.TRUE.equals(scad.getAbilitato()));
        e.setAvvAppIoPromScadPreavviso(scad != null ? scad.getPreavviso() : null);
        e.setAvvAppIoPromScadTipo(scad != null ? config.enumToText(scad.getTipo()) : null);
        e.setAvvAppIoPromScadOggetto(scad != null ? config.objToText(scad.getOggetto()) : null);
        e.setAvvAppIoPromScadMessaggio(scad != null ? config.objToText(scad.getMessaggio()) : null);

        e.setAppIoApiKey(appio != null ? appio.getApiKey() : null);
    }
}
