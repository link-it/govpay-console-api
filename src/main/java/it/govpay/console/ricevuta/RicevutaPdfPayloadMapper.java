package it.govpay.console.ricevuta;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.Versamento;
import it.govpay.stampe.client.model.Payer;
import it.govpay.stampe.client.model.Receipt;
import it.govpay.stampe.client.model.ReceiptItem;
import it.govpay.stampe.client.model.ReceiptItemStatus;
import it.govpay.stampe.client.model.ReceiptOrganization;
import it.govpay.stampe.client.model.ReceiptStatus;
import it.govpay.stampe.client.model.ReceiptVersion;

/**
 * Mapper {@link Rpt} → {@link Receipt} per la generazione PDF della ricevuta
 * tramite microservizio {@code govpay-stampe}.
 *
 * <p>Allineato a V1 ({@code RicevutaTelematica.java}, {@code RicevutaTelematicaPdf})
 * sui dati di Dominio/Versamento/Rpt, e a {@code link-it/govpay-portal-api}
 * ({@code StampeMapper.toReceipt}) per la strategia "IUR = indiceDati"
 * (semplificazione esplicita rispetto al vero IUR PagoPA nell'XML RT).
 *
 * <p>Campi <i>placeholder</i> per allineamento a portal-api:
 * <ul>
 *   <li>{@code organization.address} / {@code location}: stringhe vuote
 *       (l'anagrafica del dominio in V1 vive in {@code uo} con
 *       {@code cod_uo='EC'}, non e' mappata in questa entity slim);</li>
 *   <li>{@code psp}: {@code denominazioneAttestante} se presente, altrimenti
 *       {@code codPsp}, altrimenti {@code "N/D"}.</li>
 * </ul>
 */
@Component
public class RicevutaPdfPayloadMapper {

    private static final DateTimeFormatter DATETIME_DDMMYYYY_HHMMSS =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_DDMMYYYY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String pagopaLogoBase64;

    public RicevutaPdfPayloadMapper(@Value("${govpay.stampe.logo.pagopa:}") String pagopaLogoBase64) {
        this.pagopaLogoBase64 = pagopaLogoBase64;
    }

    public Receipt toReceipt(Rpt rpt) {
        Versamento v = rpt.getVersamento();
        Dominio d = v != null ? v.getDominio() : null;

        Receipt receipt = new Receipt();
        receipt.setCreditorLogo(creditorLogoOf(d));
        receipt.setPagopaLogo(pagopaLogoBase64);
        receipt.setPaymentSubject(v != null && v.getCausaleVersamento() != null
                ? v.getCausaleVersamento() : "");
        receipt.setOrganization(mapOrganization(d));
        receipt.setPayer(mapPayer(v));
        receipt.setPsp(pspOf(rpt));
        receipt.setAmount(rpt.getImportoTotalePagato() != null
                ? rpt.getImportoTotalePagato() : 0.0);
        receipt.setOperationDate(formatDateTime(rpt.getDataMsgRicevuta()));
        receipt.setApplicationDate(formatDate(rpt.getDataMsgRichiesta()));
        receipt.setStatus(mapStatus(rpt.getCodEsitoPagamento()));
        receipt.setCreditorReferenceId(rpt.getIuv());
        receipt.setReceiptId(rpt.getCcp());
        receipt.setObjectVersion(mapVersione(rpt.getVersione()));
        receipt.setItems(mapItems(v, rpt.getCodEsitoPagamento()));
        return receipt;
    }

    private static String creditorLogoOf(Dominio d) {
        if (d != null && d.getLogo() != null && d.getLogo().length > 0) {
            return new String(d.getLogo(), StandardCharsets.US_ASCII);
        }
        return "";
    }

    private static ReceiptOrganization mapOrganization(Dominio d) {
        ReceiptOrganization o = new ReceiptOrganization();
        o.setFiscalCode(d != null ? d.getCodDominio() : "");
        o.setBusinessName(d != null ? d.getRagioneSociale() : "");
        o.setAddress("");
        o.setLocation("");
        return o;
    }

    private static Payer mapPayer(Versamento v) {
        Payer p = new Payer();
        if (v == null) {
            p.setFiscalCode("");
            p.setFullName("");
            p.setAddress("");
            p.setLocation("");
            return p;
        }
        p.setFiscalCode(v.getDebitoreIdentificativo() != null ? v.getDebitoreIdentificativo() : "");
        p.setFullName(v.getDebitoreAnagrafica() != null ? v.getDebitoreAnagrafica() : "");
        p.setAddress(buildAddress(v));
        p.setLocation(buildLocation(v));
        return p;
    }

    private static String buildAddress(Versamento v) {
        if (!StringUtils.hasText(v.getDebitoreIndirizzo())) {
            return "";
        }
        StringBuilder sb = new StringBuilder(v.getDebitoreIndirizzo());
        if (StringUtils.hasText(v.getDebitoreCivico())) {
            sb.append(", ").append(v.getDebitoreCivico());
        }
        return sb.toString();
    }

    private static String buildLocation(Versamento v) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(v.getDebitoreCap())) {
            sb.append(v.getDebitoreCap()).append(' ');
        }
        if (StringUtils.hasText(v.getDebitoreLocalita())) {
            sb.append(v.getDebitoreLocalita());
        }
        if (StringUtils.hasText(v.getDebitoreProvincia())) {
            sb.append(" (").append(v.getDebitoreProvincia()).append(')');
        }
        return sb.toString().trim();
    }

    private static String pspOf(Rpt rpt) {
        if (StringUtils.hasText(rpt.getDenominazioneAttestante())) {
            return rpt.getDenominazioneAttestante();
        }
        if (StringUtils.hasText(rpt.getCodPsp())) {
            return rpt.getCodPsp();
        }
        return "N/D";
    }

    private static String formatDateTime(OffsetDateTime d) {
        return d != null ? d.format(DATETIME_DDMMYYYY_HHMMSS) : "N/D";
    }

    private static String formatDate(OffsetDateTime d) {
        return d != null ? d.format(DATE_DDMMYYYY) : "N/D";
    }

    /**
     * Mapping {@code cod_esito_pagamento} (codifica AgID) → {@link ReceiptStatus}:
     * 0 → EXECUTED, 1 → NOT_EXECUTED, 2 → PARTIALLY_EXECUTED,
     * 3 → EXPIRED, 4 → PARTIALLY_EXPIRED. Default: NOT_EXECUTED.
     */
    static ReceiptStatus mapStatus(Integer codEsitoPagamento) {
        if (codEsitoPagamento == null) {
            return ReceiptStatus.NOT_EXECUTED;
        }
        return switch (codEsitoPagamento) {
            case 0 -> ReceiptStatus.EXECUTED;
            case 2 -> ReceiptStatus.PARTIALLY_EXECUTED;
            case 3 -> ReceiptStatus.EXPIRED;
            case 4 -> ReceiptStatus.PARTIALLY_EXPIRED;
            default -> ReceiptStatus.NOT_EXECUTED;
        };
    }

    /**
     * Allineato a {@code link-it/govpay-portal-api} {@code StampeMapper.mapVersione}:
     * stringa SANP V1 → enum {@link ReceiptVersion} del client microservizio.
     */
    static ReceiptVersion mapVersione(String versione) {
        if (versione == null) {
            return ReceiptVersion.SANP_240;
        }
        return switch (versione) {
            case "SANP_321_V2", "RPTV1_RTV2", "RPTSANP230_RTV2" -> ReceiptVersion.SANP_240_V2;
            case "SANP_240", "RPTV2_RTV1" -> ReceiptVersion.SANP_240;
            default -> ReceiptVersion.SANP_230;
        };
    }

    private static List<ReceiptItem> mapItems(Versamento v, Integer codEsitoPagamento) {
        List<ReceiptItem> items = new ArrayList<>();
        if (v == null || v.getSingoliVersamenti() == null) {
            return items;
        }
        ReceiptItemStatus itemStatus = codEsitoPagamento != null && codEsitoPagamento == 1
                ? ReceiptItemStatus.NOT_EXECUTED
                : ReceiptItemStatus.EXECUTED;
        for (SingoloVersamento sv : v.getSingoliVersamenti()) {
            ReceiptItem item = new ReceiptItem();
            item.setDescription(sv.getDescrizione() != null
                    ? sv.getDescrizione() : "Voce di pagamento");
            item.setIur(sv.getIndiceDati() != null
                    ? String.valueOf(sv.getIndiceDati()) : "1");
            item.setAmount(sv.getImportoSingoloVersamento() != null
                    ? sv.getImportoSingoloVersamento() : 0.0);
            item.setStatus(itemStatus);
            items.add(item);
        }
        return items;
    }
}
