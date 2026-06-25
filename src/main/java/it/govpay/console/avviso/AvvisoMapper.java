package it.govpay.console.avviso;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import it.govpay.console.common.CausaleVersamentoDecoder;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.Avviso;
import it.govpay.console.model.StatoAvviso;
import it.govpay.console.pendenza.IuvUtils;

/**
 * Mapper Versamento → {@link Avviso} per la variante {@code application/json}.
 * Allineato byte-per-byte al converter V1
 * (govpay-381/.../PendenzeConverter.toAvvisoRsModel + .stato derivato),
 * con qrcode/barcode generati da {@link IuvUtils}.
 */
@Component
public class AvvisoMapper {

    private final Clock clock;

    public AvvisoMapper(Clock clock) {
        this.clock = clock;
    }

    public Avviso toAvviso(Versamento v) {
        if (v == null) {
            return null;
        }
        Avviso a = new Avviso(
                v.getDominio() != null ? v.getDominio().getCodDominio() : null,
                v.getNumeroAvviso(),
                v.getImportoTotale(),
                mapStato(v.getStatoVersamento(), v.getDataScadenza()));
        a.setDescrizione(CausaleVersamentoDecoder.decodeSimple(v.getCausaleVersamento()));
        a.setDataValidita(v.getDataValidita());
        a.setDataScadenza(v.getDataScadenza());
        a.setTassonomiaAvviso(v.getTassonomiaAvviso());
        a.setQrcode(buildQrcode(v));
        a.setBarcode(buildBarcode(v));
        return a;
    }

    StatoAvviso mapStato(String statoV1, OffsetDateTime dataScadenza) {
        if (statoV1 == null) {
            return null;
        }
        String normalized = statoV1.trim().toUpperCase();
        return switch (normalized) {
            case "ANNULLATO", "ANNULLATA" -> StatoAvviso.ANNULLATO;
            case "ESEGUITO", "ESEGUITA", "PAGATO", "PAGATA" -> StatoAvviso.PAGATO;
            case "ESEGUITO_ALTRO_CANALE" -> StatoAvviso.PAGATO;
            case "ESEGUITO_PARZIALE", "ESEGUITA_PARZIALE",
                 "PARZIALMENTE_ESEGUITO", "PAGATA_PARZIALE", "PAGATO_PARZIALE" -> StatoAvviso.PAGATO;
            case "INCASSATO", "INCASSATA", "RICONCILIATO", "RICONCILIATA" -> StatoAvviso.PAGATO;
            case "NON_ESEGUITO", "NON_ESEGUITA", "NON_PAGATO", "NON_PAGATA" ->
                    (dataScadenza != null && dataScadenza.isBefore(OffsetDateTime.now(clock)))
                            ? StatoAvviso.SCADUTO
                            : StatoAvviso.NON_PAGATO;
            case "SCADUTO", "SCADUTA" -> StatoAvviso.SCADUTO;
            default -> null;
        };
    }

    private static String buildQrcode(Versamento v) {
        if (!hasRequiredFields(v)) {
            return null;
        }
        Dominio dominio = v.getDominio();
        Stazione stazione = dominio.getStazione();
        return new String(IuvUtils.buildQrCode002(
                dominio.getCodDominio(),
                dominio.getAuxDigit(),
                stazione != null && stazione.getApplicationCode() != null
                        ? stazione.getApplicationCode()
                        : 0,
                v.getIuvVersamento(),
                BigDecimal.valueOf(v.getImportoTotale()),
                v.getNumeroAvviso()));
    }

    private static String buildBarcode(Versamento v) {
        if (!hasRequiredFields(v)) {
            return null;
        }
        Dominio dominio = v.getDominio();
        Stazione stazione = dominio.getStazione();
        return IuvUtils.buildBarCode(
                dominio.getGln() != null ? dominio.getGln() : "",
                dominio.getAuxDigit(),
                stazione != null && stazione.getApplicationCode() != null
                        ? stazione.getApplicationCode()
                        : 0,
                v.getIuvVersamento(),
                BigDecimal.valueOf(v.getImportoTotale()),
                v.getNumeroAvviso());
    }

    private static boolean hasRequiredFields(Versamento v) {
        return v.getDominio() != null
                && v.getDominio().getCodDominio() != null
                && v.getDominio().getAuxDigit() != null
                && v.getIuvVersamento() != null
                && v.getImportoTotale() != null;
    }
}
