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
import it.govpay.stampe.client.model.Iban;
import it.govpay.stampe.client.model.Languages;
import it.govpay.stampe.client.model.NoticeMetadataSecondLanguage;
import it.govpay.stampe.client.model.PaymentNotice;

/**
 * Mapping Versamento → {@link PaymentNotice} per la generazione PDF via
 * microservizio {@code govpay-stampe}.
 *
 * <p>Genera il PDF dell'avviso della singola pendenza anche quando la pendenza appartiene
 * a un documento multi-rata. I campi {@code numero_rata}, {@code tipo_soglia},
 * {@code giorni_soglia} non sono colonne fisiche su {@code versamenti}
 * — vivono solo nei DTO di input e non emergono mai in lettura da DB.
 *
 * <p><b>Limitazioni</b>:
 * <ul>
 *   <li><b>Default lingua da {@code proprieta} JSON</b>: (per ora)
 *       applichiamo solo l'override esplicito utente.</li>
 * </ul>
 *
 * <p><b>Non in scope</b>: endpoint
 * {@code GET /documenti/{...}/avviso} per il PDF aggregato dell'intero
 * documento multi-rata.
 */
@Component
public class AvvisoPdfPayloadMapper {

    /** Limiti del contratto {@code govpay-stampe.yaml} sullo schema {@code Iban}. */
    private static final int MAX_OWNER_BUSINESS_NAME = 50;
    private static final int MAX_POSTAL_AUTH_MESSAGE = 70;

    public PaymentNotice toPaymentNotice(Versamento v, LinguaSecondaria linguaSecondaria) {
        IbanAccredito postale = ibanPostale(v);

        PaymentNotice notice = new PaymentNotice();
        notice.setLanguage(Languages.IT);
        notice.setCreditor(mapCreditor(v.getDominio()));
        notice.setDebtor(mapDebtor(v));
        notice.setTitle("AVVISO DI PAGAMENTO");
        notice.setPostal(postale != null);
        notice.setFirstLogo(firstLogoOf(v.getDominio()));
        notice.setFull(mapFullAmount(v, postale));
        Languages secondaria = toClientLanguage(linguaSecondaria);
        if (secondaria != null) {
            notice.setSecondLanguage(buildSecondLanguage(secondaria));
        }
        return notice;
    }

    /**
     * Replica {@code AvvisoPagamentoV2Utils.java:441-446}: il bollettino postale
     * e' attivo se il primo singolo versamento ha un IBAN postale, di accredito
     * o (in subordine) di appoggio. Restituisce l'IBAN scelto e non un semplice
     * flag, perche' {@code govpay-stampe} lo esige: con {@code postal=true} e
     * {@code full.iban} assente rifiuta l'avviso con 422 ("Iban obbligatorio in
     * caso di avviso postale", {@code SemanticValidator}).
     */
    private static IbanAccredito ibanPostale(Versamento v) {
        if (v.getSingoliVersamenti() == null || v.getSingoliVersamenti().isEmpty()) {
            return null;
        }
        SingoloVersamento primo = v.getSingoliVersamenti().get(0);
        IbanAccredito accredito = ibanAccreditoDi(primo);
        if (isPostale(accredito)) {
            return accredito;
        }
        IbanAccredito appoggio = ibanAppoggioDi(primo);
        return isPostale(appoggio) ? appoggio : null;
    }

    /**
     * Porto di {@code SingoloVersamento.getIbanAccredito(BDConfigWrapper)} (V1,
     * {@code it.govpay.bd.model.SingoloVersamento}): l'IBAN di accredito sta
     * <b>sul singolo versamento</b> quando la pendenza e' definita
     * ({@code singoli_versamenti.id_iban_accredito} valorizzata), e <b>sul tipo
     * entrata del dominio</b> ({@code tributi}, raggiunto via
     * {@code id_tributo}) quando la pendenza e' a riferimento e la FK sul
     * singolo versamento e' nulla. Senza questo fallback l'avviso postale di
     * ogni pendenza a riferimento parte privo di IBAN.
     */
    private static IbanAccredito ibanAccreditoDi(SingoloVersamento sv) {
        if (sv.getIbanAccredito() != null) {
            return sv.getIbanAccredito();
        }
        return sv.getTributo() != null ? sv.getTributo().getIbanAccredito() : null;
    }

    /** Stessa eredita' definita/riferimento di {@link #ibanAccreditoDi}, sull'IBAN di appoggio. */
    private static IbanAccredito ibanAppoggioDi(SingoloVersamento sv) {
        if (sv.getIbanAppoggio() != null) {
            return sv.getIbanAppoggio();
        }
        return sv.getTributo() != null ? sv.getTributo().getIbanAppoggio() : null;
    }

    private static boolean isPostale(IbanAccredito iban) {
        return iban != null && Boolean.TRUE.equals(iban.getPostale());
    }

    /**
     * Dati del conto corrente postale. Si inviano i valori grezzi: numero di CC,
     * datamatrix e i fallback (intestatario assente → ente creditore,
     * autorizzazione dell'IBAN che prevale su quella del dominio) sono derivati
     * da {@code govpay-stampe} ({@code BaseAvvisoMapper.impostaDatiPostaliNellaRata},
     * {@code getAutorizzazionePostale}), che replica V1. Duplicarli qui vorrebbe
     * dire farli divergere.
     */
    private static Iban mapIban(IbanAccredito postale) {
        Iban iban = new Iban();
        iban.setIbanCode(postale.getCodIban());
        iban.setOwnerBusinessName(tronca(postale.getIntestatario(), MAX_OWNER_BUSINESS_NAME));
        iban.setPostalAuthMessage(tronca(postale.getAutStampaPoste(), MAX_POSTAL_AUTH_MESSAGE));
        return iban;
    }

    /**
     * Le colonne di {@code iban_accredito}/{@code domini} sono {@code varchar(255)},
     * i campi corrispondenti del contratto stampe hanno un {@code maxLength} piu'
     * stretto e {@code govpay-stampe} lo valida ({@code @Size}). Sono due campi di
     * sola resa grafica: troncarli stampa un avviso con l'intestazione accorciata,
     * non troncarli farebbe fallire l'intero PDF con un 400.
     */
    private static String tronca(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /**
     * Mappa l'enum {@link LinguaSecondaria} sull'enum {@link Languages} del
     * client del microservizio. {@code null} o {@link LinguaSecondaria#NONE}
     * → {@code null} (avviso solo in italiano).
     *
     * Nota: (per ora) non parsifichiamo il JSON {@code proprieta}:
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
     * dei titoli localizzati e' competenza del microservizio {@code govpay-stampe}.
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
            // Autorizzazione poste dell'ente: govpay-stampe la usa come fallback
            // quando l'IBAN postale non ne porta una propria.
            c.setPostalAuthMessage(tronca(dominio.getAutStampaPoste(), MAX_POSTAL_AUTH_MESSAGE));
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
        if (!line2.isEmpty()) {
            d.setAddressLine2(line2.toString().trim());
        }
        return d;
    }

    private static Amount mapFullAmount(Versamento v, IbanAccredito postale) {
        Amount amount = new Amount();
        amount.setAmount(v.getImportoTotale());
        amount.setNoticeNumber(v.getNumeroAvviso());
        String qrcode = AvvisoMapper.buildQrcode(v);
        if (qrcode == null) {
            throw new AvvisoNonDisponibileException(
                    "L'avviso PDF non e' disponibile: la pendenza non dispone dei dati "
                            + "necessari a comporre il codice QR (IUV, dominio o importo assenti).");
        }
        amount.setQrcode(qrcode);
        if (postale != null) {
            amount.setIban(mapIban(postale));
        }
        if (v.getDataScadenza() != null) {
            amount.setDueDate(v.getDataScadenza().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate());
        }
        return amount;
    }

    /**
     * Logo dell'ente creditore: govpay-stampe lo incorpora come TESTO base64
     * nell'XML dato in pasto a Jasper, quindi va inviato il contenuto della
     * colonna cosi' com'e' (testo base64), non i byte raw dell'immagine — che
     * romperebbero il parsing XML lato stampe. Senza logo si invia contenuto
     * vuoto e il default lo applica govpay-stampe.
     */
    private static byte[] firstLogoOf(Dominio dominio) {
        if (dominio != null && dominio.getLogo() != null && dominio.getLogo().length > 0) {
            return dominio.getLogo();
        }
        return new byte[0];
    }

    static LocalDate dueDateOf(Versamento v) {
        return v.getDataScadenza() == null
                ? null
                : v.getDataScadenza().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
    }
}
