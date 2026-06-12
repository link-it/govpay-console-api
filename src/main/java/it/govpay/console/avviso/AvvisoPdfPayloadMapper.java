package it.govpay.console.avviso;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.LinguaSecondaria;
import it.govpay.stampe.client.model.Amount;
import it.govpay.stampe.client.model.Creditor;
import it.govpay.stampe.client.model.Debtor;
import it.govpay.stampe.client.model.Languages;
import it.govpay.stampe.client.model.NoticeMetadataSecondLanguage;
import it.govpay.stampe.client.model.PaymentNotice;

/**
 * Mapping Versamento → {@link PaymentNotice} per la generazione PDF via
 * microservizio {@code govpay-stampe}.
 *
 * <p>Allineato all'endpoint {@code /avvisi} di V1
 * ({@code AvvisiDAO.getAvviso} → {@code printAvvisoVersamento} →
 * {@code AvvisoPagamentoV2Utils.fromVersamento}): genera il PDF dell'avviso
 * della singola pendenza anche quando la pendenza appartiene a un documento
 * multi-rata. I campi {@code numero_rata}, {@code tipo_soglia},
 * {@code giorni_soglia} di V1 non sono colonne fisiche su {@code versamenti}
 * — vivono solo nei DTO di input e non emergono mai in lettura da DB.
 *
 * <p><b>Limitazioni</b>:
 * <ul>
 *   <li><b>Default lingua da {@code proprieta} JSON</b>: V1 cerca un default in
 *       {@code versamento.proprietaPendenza.linguaSecondaria}; in V2 (per ora)
 *       applichiamo solo l'override esplicito utente.</li>
 * </ul>
 *
 * <p><b>Non in scope (issue successiva)</b>: endpoint
 * {@code GET /documenti/{...}/avviso} per il PDF aggregato dell'intero
 * documento multi-rata (V1: {@code AvvisiDAO.getDocumento} →
 * {@code AvvisoPagamentoV2Utils.fromDocumento}).
 */
@Component
public class AvvisoPdfPayloadMapper {

    public PaymentNotice toPaymentNotice(Versamento v, LinguaSecondaria linguaSecondaria) {
        PaymentNotice notice = new PaymentNotice();
        notice.setLanguage(Languages.IT);
        notice.setCreditor(mapCreditor(v.getDominio()));
        notice.setDebtor(mapDebtor(v));
        notice.setTitle("AVVISO DI PAGAMENTO");
        notice.setPostal(hasBollettinoPostale(v));
        notice.setFull(mapFullAmount(v));
        Languages secondaria = toClientLanguage(linguaSecondaria);
        if (secondaria != null) {
            notice.setSecondLanguage(buildSecondLanguage(secondaria));
        }
        return notice;
    }

    /**
     * Replica {@code AvvisoPagamentoV2Utils.java:441-446}: il bollettino
     * postale e' attivo se il primo singolo versamento ha un IBAN postale,
     * di accredito o di appoggio.
     */
    private static Boolean hasBollettinoPostale(Versamento v) {
        if (v.getSingoliVersamenti() == null || v.getSingoliVersamenti().isEmpty()) {
            return Boolean.FALSE;
        }
        SingoloVersamento primo = v.getSingoliVersamenti().get(0);
        return isPostale(primo.getIbanAccredito()) || isPostale(primo.getIbanAppoggio())
                ? Boolean.TRUE
                : Boolean.FALSE;
    }

    private static boolean isPostale(IbanAccredito iban) {
        return iban != null && Boolean.TRUE.equals(iban.getPostale());
    }

    /**
     * Mappa l'enum V2 {@link LinguaSecondaria} sull'enum {@link Languages} del
     * client del microservizio. {@code null} o {@link LinguaSecondaria#NONE}
     * → {@code null} (avviso solo in italiano).
     *
     * Nota: V1 cerca anche un default in
     * {@code versamento.proprietaPendenza.linguaSecondaria} se l'utente non passa
     * il parametro. In V2 (per ora) non parsifichiamo il JSON {@code proprieta}:
     * applichiamo solo l'override esplicito utente.
     */
    static Languages toClientLanguage(LinguaSecondaria input) {
        if (input == null || input == LinguaSecondaria.NONE) {
            return null;
        }
        return switch (input) {
            case DE -> Languages.DE;
            case EN -> Languages.EN;
            case FR -> Languages.FR;
            case SL -> Languages.SL;
            default -> null;
        };
    }

    /**
     * Costruisce {@link NoticeMetadataSecondLanguage} con {@code bilinguism=true}
     * e la lingua selezionata. Il {@code title} resta {@code null}: il rendering
     * dei titoli localizzati e' competenza del microservizio
     * {@code govpay-stampe} (template {@code LabelAvvisiProperties} di V1).
     */
    private static NoticeMetadataSecondLanguage buildSecondLanguage(Languages lang) {
        NoticeMetadataSecondLanguage sl = new NoticeMetadataSecondLanguage();
        sl.setBilinguism(Boolean.TRUE);
        sl.setLanguage(lang);
        return sl;
    }

    private static Creditor mapCreditor(Dominio dominio) {
        Creditor c = new Creditor();
        if (dominio != null) {
            c.setFiscalCode(dominio.getCodDominio());
            c.setBusinessName(dominio.getRagioneSociale());
        }
        return c;
    }

    private static Debtor mapDebtor(Versamento v) {
        Debtor d = new Debtor();
        d.setFiscalCode(v.getDebitoreIdentificativo());
        d.setFullName(v.getDebitoreAnagrafica());
        if (StringUtils.hasText(v.getDebitoreIndirizzo())) {
            StringBuilder line1 = new StringBuilder(v.getDebitoreIndirizzo());
            if (StringUtils.hasText(v.getDebitoreCivico())) {
                line1.append(", ").append(v.getDebitoreCivico());
            }
            d.setAddressLine1(line1.toString());
        }
        StringBuilder line2 = new StringBuilder();
        if (StringUtils.hasText(v.getDebitoreCap())) {
            line2.append(v.getDebitoreCap()).append(' ');
        }
        if (StringUtils.hasText(v.getDebitoreLocalita())) {
            line2.append(v.getDebitoreLocalita());
        }
        if (StringUtils.hasText(v.getDebitoreProvincia())) {
            line2.append(" (").append(v.getDebitoreProvincia()).append(')');
        }
        if (line2.length() > 0) {
            d.setAddressLine2(line2.toString().trim());
        }
        return d;
    }

    private static Amount mapFullAmount(Versamento v) {
        Amount amount = new Amount();
        amount.setAmount(v.getImportoTotale());
        amount.setNoticeNumber(v.getNumeroAvviso());
        if (v.getDataScadenza() != null) {
            amount.setDueDate(v.getDataScadenza().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate());
        }
        return amount;
    }

    static LocalDate dueDateOf(Versamento v) {
        return v.getDataScadenza() == null
                ? null
                : v.getDataScadenza().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
    }
}
