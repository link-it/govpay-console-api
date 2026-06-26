package it.govpay.console.ricevuta;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;

import it.govpay.console.common.CausaleVersamentoDecoder;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.Link;
import it.govpay.console.model.PendenzaRef;
import it.govpay.console.model.Ricevuta;
import it.govpay.console.model.RicevutaLinks;
import it.govpay.console.model.RicevutaSummary;
import it.govpay.console.ricevuta.pagopa.RptRtJsonConverter;

/**
 * Mapper {@link Rpt} → modelli ricevuta:
 * <ul>
 *   <li>{@link #toSummary(Rpt)} — proiezione metadata-only per le liste;</li>
 *   <li>{@link #toRicevuta(Rpt)} — dettaglio canonico con conversione JSON di
 *       {@code rpt}/{@code rt} (via {@link RptRtJsonConverter}), riferimento alla
 *       pendenza e hyperlink ai sub-resource.</li>
 * </ul>
 *
 * <p>{@code segnalazioni} non è popolato: V1 espone il campo ma il converter
 * backoffice non lo valorizza mai (nessuna fonte DB), quindi resta vuoto.
 */
@Component
public class RicevutaMapper {

    private final RptRtJsonConverter rptRtJsonConverter;

    public RicevutaMapper(RptRtJsonConverter rptRtJsonConverter) {
        this.rptRtJsonConverter = rptRtJsonConverter;
    }

    /**
     * Proiezione metadata-only di una RT. Tupla identificante {@code (idDominio,
     * iuv, idRicevuta)} dove {@code idRicevuta} era il {@code ccp} di V1.
     */
    public RicevutaSummary toSummary(Rpt rpt) {
        RicevutaSummary s = new RicevutaSummary();
        s.setIdDominio(rpt.getCodDominio());
        s.setIuv(rpt.getIuv());
        s.setIdRicevuta(rpt.getCcp());
        s.setDataPagamento(rpt.getDataMsgRicevuta());
        s.setCodPsp(rpt.getCodPsp());
        s.setVersione(versioneProtocollo(rpt.getVersione()));
        s.setStato(rpt.getStato());
        s.setDescrizioneStato(rpt.getDescrizioneStato());
        s.setImporto(rpt.getImportoTotalePagato());
        return s;
    }

    /** Dettaglio canonico: i 9 metadati della summary + rpt/rt JSON + pendenza + _links. */
    public Ricevuta toRicevuta(Rpt rpt) {
        if (rpt == null) {
            return null;
        }
        Ricevuta r = new Ricevuta();
        r.setIdDominio(rpt.getCodDominio());
        r.setIuv(rpt.getIuv());
        r.setIdRicevuta(rpt.getCcp());
        r.setDataPagamento(rpt.getDataMsgRicevuta());
        r.setCodPsp(rpt.getCodPsp());
        r.setVersione(versioneProtocollo(rpt.getVersione()));
        r.setStato(rpt.getStato());
        r.setDescrizioneStato(rpt.getDescrizioneStato());
        r.setImporto(rpt.getImportoTotalePagato());
        // rpt è nullable nel contratto (standin senza richiesta): JsonNullable.of(null)
        // serializza esplicitamente "rpt": null.
        r.setRpt(JsonNullable.of(rptRtJsonConverter.toRptMap(rpt)));
        r.setRt(rptRtJsonConverter.toRtMap(rpt));
        r.setPendenza(pendenzaRef(rpt.getVersamento()));
        r.setLinks(buildLinks(rpt));
        return r;
    }

    private static PendenzaRef pendenzaRef(Versamento v) {
        if (v == null || v.getApplicazione() == null) {
            return null;
        }
        PendenzaRef ref = new PendenzaRef(v.getApplicazione().getCodApplicazione(), v.getCodVersamentoEnte());
        ref.setCausaleBreve(CausaleVersamentoDecoder.decodeSimple(v.getCausaleVersamento()));
        return ref;
    }

    private static RicevutaLinks buildLinks(Rpt rpt) {
        String base = "/ricevute/" + rpt.getCodDominio() + "/" + rpt.getIuv() + "/" + segment(rpt.getCcp());
        RicevutaLinks links = new RicevutaLinks(new Link(base + "/rpt"), new Link(base + "/rt"));
        Versamento v = rpt.getVersamento();
        if (v != null && v.getApplicazione() != null) {
            links.setPendenza(new Link(
                    "/pendenze/" + v.getApplicazione().getCodApplicazione() + "/" + v.getCodVersamentoEnte()));
        }
        return links;
    }

    /** Codifica un singolo segmento di path (es. il legacy {@code n/a} → {@code n%2Fa}). */
    private static String segment(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Versione del protocollo pagoPA della RT, derivata dalla generazione SANP
     * della transazione in base al formato del messaggio di ricevuta:
     * {@code 1.0} = RT formato v1 ({@code CtRicevutaTelematica}/{@code CtReceipt}),
     * {@code 2.0} = RT formato v2 ({@code CtReceiptV2}). Coerente con il dispatch
     * RT→JSON di {@code RptRtJsonConverter}. Versione sconosciuta → {@code null}.
     */
    static String versioneProtocollo(String versioneSanp) {
        if (versioneSanp == null) {
            return null;
        }
        return switch (versioneSanp) {
            case "SANP_230", "SANP_240", "RPTV2_RTV1" -> "1.0";
            case "SANP_321_V2", "RPTV1_RTV2", "RPTSANP230_RTV2" -> "2.0";
            default -> null;
        };
    }
}
