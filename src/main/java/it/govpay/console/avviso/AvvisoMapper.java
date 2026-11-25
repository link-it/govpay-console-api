package it.govpay.console.avviso;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import it.govpay.console.common.CausaleVersamentoDecoder;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Stazione;
import it.govpay.console.model.Avviso;
import it.govpay.console.model.StatoAvviso;
import it.govpay.common.utils.IuvUtils;
import it.govpay.pendenze.entity.Pendenza;

/**
 * Mapper pendenza v3 → {@link Avviso} per la variante {@code application/json}.
 * qrcode/barcode generati da {@link IuvUtils}.
 */
@Component
public class AvvisoMapper {

    private final Clock clock;

    public AvvisoMapper(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param p       pendenza con opzione di pagamento e posizione debitoria caricate
     * @param dominio dominio della pendenza
     */
    public Avviso toAvviso(Pendenza p, Dominio dominio) {
        if (p == null) {
            return null;
        }
        Avviso a = new Avviso(
                dominio != null ? dominio.getCodDominio() : null,
                p.getNumeroAvviso(),
                p.getImporto(),
                mapStato(p.getStato(), p.getDataScadenzaAvviso()));
        a.setDescrizione(oggettoDelPagamento(p));
        a.setDataValidita(p.getDataValidita());
        a.setDataScadenza(p.getDataScadenzaAvviso());
        a.setTassonomiaAvviso(p.getTassonomiaAvviso());
        a.setQrcode(buildQrcode(p, dominio));
        a.setBarcode(buildBarcode(p, dominio));
        return a;
    }

    /**
     * Oggetto del pagamento: la causale della pendenza se presente, altrimenti la descrizione
     * della sua posizione debitoria.
     */
    static String oggettoDelPagamento(Pendenza p) {
        String causale = CausaleVersamentoDecoder.decodeSimple(p.getCausaleVersamento());
        return causale != null ? causale : p.getOpzionePagamento().getPosizioneDebitoria().getDescrizione();
    }

    StatoAvviso mapStato(it.govpay.pendenze.model.StatoPendenza stato, OffsetDateTime dataScadenza) {
        if (stato == null) {
            return null;
        }
        return switch (stato) {
            case ANNULLATO -> StatoAvviso.ANNULLATO;
            case ESEGUITO, ESEGUITO_ALTRO_CANALE, ESEGUITO_SENZA_RPT, PARZIALMENTE_ESEGUITO, INCASSATO ->
                    StatoAvviso.PAGATO;
            case NON_ESEGUITO -> dataScadenza != null && dataScadenza.isBefore(OffsetDateTime.now(clock))
                    ? StatoAvviso.SCADUTO
                    : StatoAvviso.NON_PAGATO;
            case ANOMALO -> null;
        };
    }

    /**
     * Stringa QR {@code PAGOPA|002|...}. {@code null} se mancano i dati di base (dominio,
     * auxDigit, IUV). Condivisa con il payload PDF ({@link AvvisoPdfPayloadMapper}).
     */
    static String buildQrcode(Pendenza p, Dominio dominio) {
        if (!hasRequiredFields(p, dominio)) {
            return null;
        }
        Stazione stazione = dominio.getStazione();
        return IuvUtils.buildQrCode002(
                dominio.getCodDominio(),
                dominio.getAuxDigit(),
                stazione != null && stazione.getApplicationCode() != null
                        ? stazione.getApplicationCode()
                        : 0,
                p.getIuv(),
                BigDecimal.valueOf(p.getImporto()),
                p.getNumeroAvviso());
    }

    private static String buildBarcode(Pendenza p, Dominio dominio) {
        if (!hasRequiredFields(p, dominio)) {
            return null;
        }
        Stazione stazione = dominio.getStazione();
        return IuvUtils.buildBarCode(
                dominio.getGln() != null ? dominio.getGln() : "",
                dominio.getAuxDigit(),
                stazione != null && stazione.getApplicationCode() != null
                        ? stazione.getApplicationCode()
                        : 0,
                p.getIuv(),
                BigDecimal.valueOf(p.getImporto()),
                p.getNumeroAvviso());
    }

    private static boolean hasRequiredFields(Pendenza p, Dominio dominio) {
        return dominio != null
                && dominio.getCodDominio() != null
                && dominio.getAuxDigit() != null
                && p.getIuv() != null;
    }
}
