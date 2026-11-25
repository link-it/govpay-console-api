package it.govpay.console.avviso;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.databind.ObjectMapper;

import it.govpay.console.common.CausaleVersamentoDecoder;
import it.govpay.console.dominio.DominioLogoCodec;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.model.LinguaSecondaria;
import it.govpay.console.web.BadRequestException;
import it.govpay.stampe.client.model.Amount;
import it.govpay.stampe.client.model.CdsViolation;
import it.govpay.stampe.client.model.Creditor;
import it.govpay.stampe.client.model.Debtor;
import it.govpay.stampe.client.model.Iban;
import it.govpay.stampe.client.model.Instalment;
import it.govpay.stampe.client.model.Languages;
import it.govpay.stampe.client.model.NoticeMetadataSecondLanguage;
import it.govpay.pendenze.entity.Debitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import it.govpay.stampe.client.model.PaymentNotice;
import it.govpay.stampe.client.model.PaymentNoticeOption;
import it.govpay.stampe.client.model.ThresholdPayment;
import it.govpay.stampe.client.model.ThresholdType;

/**
 * Mapping pendenza v3 → {@link PaymentNotice} per la generazione PDF via
 * microservizio {@code govpay-stampe}.
 *
 * <p>Genera il PDF dell'avviso della singola pendenza, anche quando appartiene a un'opzione
 * a piu' rate: un bollettino per pendenza. Il debitore e' quello dell'opzione della pendenza.
 *
 * <p><b>Limitazioni</b>:
 * <ul>
 *   <li><b>Default lingua da {@code proprieta} JSON</b>: (per ora)
 *       applichiamo solo l'override esplicito utente.</li>
 *   <li><b>Altri campi di {@code proprieta}</b> (descrizioneImporto, lineaTestoRicevuta1/2,
 *       dataScandenzaAvviso): non letti — solo {@code informativaImportoAvviso},
 *       {@code linguaSecondariaCausale} e {@code linguaSecondariaInformativaImportoAvviso}
 *       (vedi {@link #proprietaDi}).</li>
 * </ul>
 *
 * <p><b>Non in scope</b>: endpoint
 * {@code GET /documenti/{...}/avviso} per il PDF aggregato dell'intero
 * documento multi-rata.
 */
@Component
public class AvvisoPdfPayloadMapper {

    private static final Logger log = LoggerFactory.getLogger(AvvisoPdfPayloadMapper.class);

    /** Limiti del contratto {@code govpay-stampe.yaml} sullo schema {@code Iban}. */
    private static final int MAX_OWNER_BUSINESS_NAME = 50;
    private static final int MAX_POSTAL_AUTH_MESSAGE = 70;

    /** Limite del contratto {@code govpay-stampe.yaml} su {@code Creditor.business_name}. */
    private static final int MAX_BUSINESS_NAME = 50;

    /** Limite del contratto {@code govpay-stampe.yaml} su {@code Debtor.full_name}. */
    private static final int MAX_FULL_NAME = 70;

    /**
     * Limite del contratto {@code govpay-stampe.yaml} su {@code Debtor.address_line_1}/
     * {@code address_line_2}.
     */
    private static final int MAX_ADDRESS_LINE = 70;

    private final ObjectMapper objectMapper;

    public AvvisoPdfPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param p        pendenza con opzione di pagamento, debitore e posizione caricati
     * @param dominio  dominio della pendenza
     * @param postale  IBAN postale della pendenza, o {@code null} se non ha bollettino postale
     */
    public PaymentNotice toPaymentNotice(Pendenza p, Dominio dominio, IbanAccredito postale,
                                         LinguaSecondaria linguaSecondaria) {
        ProprietaPendenza proprieta = proprietaDi(p);

        PaymentNotice notice = new PaymentNotice();
        notice.setLanguage(Languages.IT);
        notice.setCreditor(mapCreditor(dominio, pagamentoSito(dominio)));
        notice.setDebtor(mapDebtor(debitoreDi(p)));
        // 'title' e' l'oggetto del pagamento (oggetto_del_pagamento/XSD), non un'intestazione
        // fissa: stesso campo del legacy v1 (AvvisoPagamentoUtils.fromVersamento), che ci
        // scrive la causale vera.
        notice.setTitle(AvvisoMapper.oggettoDelPagamento(p));
        notice.setPostal(postale != null);
        notice.setFirstLogo(firstLogoOf(dominio));
        if (isSogliaTemporale(p)) {
            notice.setReducedPayments(List.of(mapReducedPayment(p, dominio, postale)));
        } else {
            notice.setFull(mapFullAmount(p, dominio, postale));
        }
        notice.setInformativaImporto(proprieta != null ? proprieta.getInformativaImportoAvviso() : null);
        Languages secondaria = toClientLanguage(linguaSecondaria);
        if (secondaria != null) {
            String causaleTradotta = proprieta != null ? proprieta.getLinguaSecondariaCausale() : null;
            if (causaleTradotta == null) {
                throw new BadRequestException(
                        "la pendenza non ha una causale tradotta configurata "
                                + "(proprieta.linguaSecondariaCausale): impossibile generare l'avviso bilingue.",
                        "linguaSecondaria");
            }
            notice.setSecondLanguage(buildSecondLanguage(secondaria, causaleTradotta,
                    proprieta.getLinguaSecondariaInformativaImportoAvviso()));
        }
        return notice;
    }

    /**
     * {@code null} se {@code proprieta} e' assente/vuoto o non interpretabile come JSON — un
     * blob malformato non deve far fallire la generazione dell'avviso per campi puramente
     * accessori ({@code informativaImportoAvviso}/{@code linguaSecondariaCausale}/
     * {@code linguaSecondariaInformativaImportoAvviso}): si logga e si procede come se non
     * fossero presenti.
     */
    private ProprietaPendenza proprietaDi(Pendenza p) {
        String proprieta = p.getProprieta();
        if (!StringUtils.hasText(proprieta)) {
            return null;
        }
        try {
            return objectMapper.readValue(proprieta, ProprietaPendenza.class);
        } catch (RuntimeException e) {
            log.warn("Pendenza [{}]: proprieta non interpretabile come JSON, ignorata.",
                    p.getIdPendenza(), e);
            return null;
        }
    }

    /**
     * La pendenza e' una Marca da Bollo Telematica se almeno una voce ha {@code hashDocumento},
     * {@code provinciaResidenza} e {@code tipoBollo} tutti valorizzati.
     */
    public static boolean isPendenzaMbt(Pendenza p) {
        return isPendenzaMbt(p.getVoci());
    }

    /** Come {@link #isPendenzaMbt(Pendenza)}, sulle voci gia' caricate. */
    public static boolean isPendenzaMbt(java.util.Collection<VocePendenza> voci) {
        if (voci == null) {
            return false;
        }
        for (VocePendenza voce : voci) {
            if (voce.getHashDocumento() != null
                    && voce.getProvinciaResidenza() != null
                    && voce.getTipoBollo() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nel modello v3 l'anagrafica del debitore e' facoltativa, mentre govpay-stampe richiede
     * il nome del debitore: l'avviso non si puo' stampare.
     */
    public static boolean isAnagraficaDebitoreAssente(Pendenza p) {
        return !StringUtils.hasText(debitoreDi(p).getAnagrafica());
    }

    /** Il debitore della pendenza e' quello della sua opzione di pagamento. */
    private static Debitore debitoreDi(Pendenza p) {
        return p.getOpzionePagamento().getPdDebitore().getDebitore();
    }

    /** {@code true} se la pendenza appartiene a un'opzione {@code SOLUZIONE_UNICA_ENTRO}/{@code _OLTRE}. */
    private static boolean isSogliaTemporale(Pendenza p) {
        TipologiaOpzionePagamento tipologia = p.getOpzionePagamento().getTipologia();
        return tipologia == TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO
                || tipologia == TipologiaOpzionePagamento.SOLUZIONE_UNICA_OLTRE;
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
        return value != null ? troncaObbligatorio(value, max) : null;
    }

    /**
     * Per i campi obbligatori del contratto ({@code creditor.businessName},
     * {@code debtor.fullName}): un valore assente diventa stringa vuota invece di
     * {@code null}. Gli opzionali passano da {@link #tronca}, che conserva il
     * {@code null}: per l'IBAN postale l'assenza attiva i fallback di {@code govpay-stampe}.
     */
    private static String troncaObbligatorio(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
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
     * Costruisce {@link NoticeMetadataSecondLanguage} con {@code bilinguism=true}, la lingua
     * selezionata, la causale tradotta se configurata ({@code proprieta.linguaSecondariaCausale}
     * — stesso campo del legacy) e la nota-importo tradotta se configurata
     * ({@code proprieta.linguaSecondariaInformativaImportoAvviso}). Nessun fallback sul testo
     * italiano, per non mostrare testo non tradotto etichettato come lingua secondaria:
     * {@code informativaImporto} non configurata resta {@code null}, mentre {@code title} e'
     * richiesto dallo schema di govpay-stampe e senza causale tradotta l'avviso bilingue e'
     * rifiutato con un 400 in {@link #toPaymentNotice}, invece di lasciare che govpay-stampe
     * lo rifiuti a sua volta e far arrivare al chiamante un 502 generico.
     *
     * <p>{@code informativaImporto} qui viene sempre passato cosi' com'e': e' govpay-stampe-api
     * (non questo mapper) a leggerlo solo se {@code PaymentNotice.informativaImporto} e'
     * valorizzato, stesso vincolo del legacy.</p>
     */
    private static NoticeMetadataSecondLanguage buildSecondLanguage(Languages lang, String causaleTradotta,
            String informativaImportoTradotta) {
        NoticeMetadataSecondLanguage sl = new NoticeMetadataSecondLanguage();
        sl.setBilinguism(Boolean.TRUE);
        sl.setLanguage(lang);
        sl.setTitle(causaleTradotta);
        sl.setInformativaImporto(informativaImportoTradotta);
        return sl;
    }

    /** L'ente consente il pagamento anche dal proprio sito ({@code domini.pagamento_sito}). */
    private static boolean pagamentoSito(Dominio dominio) {
        return dominio != null && Boolean.TRUE.equals(dominio.getPagamentoSito());
    }

    private static Creditor mapCreditor(Dominio dominio, boolean payOnWebsite) {
        Creditor c = new Creditor();
        if (dominio != null) {
            c.setFiscalCode(dominio.getCodDominio());
            c.setBusinessName(troncaObbligatorio(dominio.getRagioneSociale(), MAX_BUSINESS_NAME));
            c.setCbillCode(dominio.getCbill());
            // Autorizzazione poste dell'ente: govpay-stampe la usa come fallback
            // quando l'IBAN postale non ne porta una propria.
            c.setPostalAuthMessage(tronca(dominio.getAutStampaPoste(), MAX_POSTAL_AUTH_MESSAGE));
        }
        if (payOnWebsite) {
            c.setPayOnWebsite(Boolean.TRUE);
        }
        return c;
    }

    /**
     * {@code addressLine1}/{@code addressLine2} troncano DOPO la concatenazione: i limiti dello
     * schema (50/70 caratteri) sono quelli imposti da pagoPA sul singolo campo del bollettino,
     * non sulle singole colonne DB che li compongono (es. {@code debitore_indirizzo varchar(70)}
     * + {@code debitore_civico varchar(16)} possono insieme superare 70) — troncare le colonne
     * singolarmente prima di concatenarle non basterebbe a garantire il rispetto del limite.
     */
    private static Debtor mapDebtor(Debitore v) {
        Debtor d = new Debtor();
        d.setFiscalCode(v.getIdentificativo());
        d.setFullName(troncaObbligatorio(v.getAnagrafica(), MAX_FULL_NAME));
        if (StringUtils.hasText(v.getIndirizzo())) {
            StringBuilder line1 = new StringBuilder(v.getIndirizzo());
            if (StringUtils.hasText(v.getCivico())) {
                line1.append(", ").append(v.getCivico());
            }
            d.setAddressLine1(tronca(line1.toString(), MAX_ADDRESS_LINE));
        }
        StringBuilder line2 = new StringBuilder();
        if (StringUtils.hasText(v.getCap())) {
            line2.append(v.getCap()).append(' ');
        }
        if (StringUtils.hasText(v.getLocalita())) {
            line2.append(v.getLocalita());
        }
        if (StringUtils.hasText(v.getProvincia())) {
            line2.append(" (").append(v.getProvincia()).append(')');
        }
        if (!line2.isEmpty()) {
            d.setAddressLine2(tronca(line2.toString().trim(), MAX_ADDRESS_LINE));
        }
        return d;
    }

    private static Amount mapFullAmount(Pendenza p, Dominio dominio, IbanAccredito postale) {
        Amount amount = new Amount();
        amount.setAmount(p.getImporto());
        amount.setNoticeNumber(p.getNumeroAvviso());
        amount.setQrcode(qrcodeObbligatorio(p, dominio));
        if (postale != null) {
            amount.setIban(mapIban(postale));
        }
        LocalDate scadenza = dueDateOf(p);
        if (scadenza != null) {
            amount.setDueDate(scadenza);
        }
        return amount;
    }

    /** Stessi campi di {@link #mapFullAmount}, piu' tipo e giorni della soglia dall'opzione. */
    private static ThresholdPayment mapReducedPayment(Pendenza p, Dominio dominio, IbanAccredito postale) {
        ThresholdPayment reduced = new ThresholdPayment();
        reduced.setAmount(p.getImporto());
        reduced.setNoticeNumber(p.getNumeroAvviso());
        reduced.setQrcode(qrcodeObbligatorio(p, dominio));
        LocalDate scadenza = dueDateOf(p);
        if (scadenza != null) {
            reduced.setDueDate(scadenza);
        }
        reduced.setThresholdType(p.getOpzionePagamento().getTipologia()
                == TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO ? ThresholdType.ENTRO : ThresholdType.OLTRE);
        reduced.setThresholdDays(p.getOpzionePagamento().getGiorni());
        if (postale != null) {
            reduced.setIban(mapIban(postale));
        }
        return reduced;
    }

    private static String qrcodeObbligatorio(Pendenza p, Dominio dominio) {
        String qrcode = AvvisoMapper.buildQrcode(p, dominio);
        if (qrcode == null) {
            throw new AvvisoNonDisponibileException(
                    "L'avviso PDF non e' disponibile: la pendenza non dispone dei dati "
                            + "necessari a comporre il codice QR (IUV o dominio assenti).");
        }
        return qrcode;
    }

    /**
     * Logo dell'ente creditore: govpay-stampe lo incorpora come TESTO base64
     * nell'XML dato in pasto a Jasper, quindi va inviato il contenuto della
     * colonna cosi' com'e' (testo base64), non i byte raw dell'immagine — che
     * romperebbero il parsing XML lato stampe. Senza logo si invia contenuto
     * vuoto e il default lo applica govpay-stampe. Lo stesso vale per un logo
     * in archivio che non e' un'immagine in Base64 (issue #124).
     */
    private static byte[] firstLogoOf(Dominio dominio) {
        if (dominio != null && DominioLogoCodec.leggi(dominio.getLogo()).isPresent()) {
            return dominio.getLogo();
        }
        return new byte[0];
    }

    // --- avviso complessivo della posizione ---

    /** Dominio e IBAN postale di una pendenza, risolti dal chiamante in blocco. */
    public interface Risolutore {
        Dominio dominio(Pendenza pendenza);

        IbanAccredito iban(Pendenza pendenza);
    }

    /**
     * Avviso di una sola pendenza stampata come posizione: titolo sempre la descrizione della
     * posizione, mai la causale della pendenza. Solo italiano, nessuna informativa personalizzata.
     */
    public PaymentNotice toPaymentNoticeDocumento(Pendenza p, Risolutore r) {
        Dominio dominio = r.dominio(p);
        IbanAccredito postale = r.iban(p);
        PaymentNotice notice = intestazione(p, dominio, descrizioneDi(p));
        notice.setPostal(postale != null);
        if (isSogliaTemporale(p)) {
            notice.setReducedPayments(List.of(mapReducedPayment(p, dominio, postale)));
        } else {
            notice.setFull(mapFullAmount(p, dominio, postale));
        }
        return notice;
    }

    /**
     * Avviso a rate di una sola opzione: una {@link Instalment} per pendenza, nell'ordine
     * ricevuto. Le notizie del documento vengono dalla prima rata. Il bollettino postale e'
     * attivo solo se tutte le rate hanno un IBAN postale.
     *
     * @param rate pendenze gia' filtrate e ordinate, non vuota
     */
    public PaymentNotice toPaymentNoticeRate(List<Pendenza> rate, Risolutore r) {
        Pendenza prima = rate.get(0);
        PaymentNotice notice = intestazione(prima, r.dominio(prima), descrizioneDi(prima));
        notice.setPostal(rate.stream().allMatch(p -> r.iban(p) != null));
        List<Instalment> instalments = new ArrayList<>(rate.size());
        for (Pendenza rata : rate) {
            instalments.add(mapInstalment(rata, r.dominio(rata), r.iban(rata)));
        }
        notice.setInstalments(instalments);
        return notice;
    }

    /**
     * Avviso con piu' opzioni alternative della stessa posizione, una per elemento di
     * {@code options}. Postale solo se tutte le pendenze di tutte le opzioni hanno un IBAN
     * postale.
     *
     * @param opzioni le pendenze pagabili di ciascuna opzione, almeno due opzioni
     */
    public PaymentNotice toPaymentNoticeOpzioni(List<List<Pendenza>> opzioni, Risolutore r) {
        Pendenza prima = opzioni.get(0).get(0);
        PaymentNotice notice = intestazione(prima, r.dominio(prima), descrizioneDi(prima));
        notice.setPostal(opzioni.stream().flatMap(List::stream).allMatch(p -> r.iban(p) != null));
        List<PaymentNoticeOption> options = new ArrayList<>(opzioni.size());
        for (List<Pendenza> pendenzeOpzione : opzioni) {
            options.add(toOption(pendenzeOpzione, r));
        }
        notice.setOptions(options);
        return notice;
    }

    /**
     * Avviso con la coppia {@code SOLUZIONE_UNICA_ENTRO}/{@code _OLTRE} sulla stessa pagina:
     * un pagamento a soglia per pendenza, per giorni crescenti e, a parita', ENTRO prima di OLTRE.
     */
    public PaymentNotice toPaymentNoticeSoglie(List<Pendenza> soglie, Risolutore r) {
        List<Pendenza> ordinate = soglie.stream()
                .sorted(Comparator.comparingInt((Pendenza p) -> p.getOpzionePagamento().getGiorni())
                        .thenComparing(p -> p.getOpzionePagamento().getTipologia()
                                == TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO ? 0 : 1))
                .toList();
        Pendenza prima = ordinate.get(0);
        PaymentNotice notice = intestazione(prima, r.dominio(prima), descrizioneDi(prima));
        notice.setPostal(ordinate.stream().allMatch(p -> r.iban(p) != null));
        List<ThresholdPayment> ridotti = new ArrayList<>(ordinate.size());
        for (Pendenza p : ordinate) {
            ridotti.add(mapReducedPayment(p, r.dominio(p), r.iban(p)));
        }
        notice.setReducedPayments(ridotti);
        return notice;
    }

    /**
     * Violazione del Codice della Strada con importo ridotto e scontato.
     *
     * @param opzioni le due opzioni (una pendenza ciascuna), come verificato da {@link #sonoRidottoEScontato}
     */
    public CdsViolation toCdsViolation(List<List<Pendenza>> opzioni, Risolutore r) {
        Pendenza ridotto = null;
        Pendenza scontato = null;
        for (List<Pendenza> opzione : opzioni) {
            Pendenza p = opzione.get(0);
            if (p.getOpzionePagamento().getTipologia() == TipologiaOpzionePagamento.SOLUZIONE_UNICA_RIDOTTO) {
                ridotto = p;
            } else {
                scontato = p;
            }
        }
        Dominio dominio = r.dominio(ridotto);
        boolean postale = r.iban(ridotto) != null && r.iban(scontato) != null;
        CdsViolation violazione = new CdsViolation();
        violazione.setLanguage(Languages.IT);
        violazione.setCreditor(mapCreditor(dominio, pagamentoSito(dominio)));
        violazione.setDebtor(mapDebtor(debitoreDi(ridotto)));
        violazione.setTitle(descrizioneDi(ridotto));
        violazione.setFirstLogo(firstLogoOf(dominio));
        violazione.setPostal(postale);
        violazione.setReducedAmount(mapFullAmount(ridotto, dominio, postale ? r.iban(ridotto) : null));
        violazione.setDiscountedAmount(
                mapFullAmount(scontato, r.dominio(scontato), postale ? r.iban(scontato) : null));
        return violazione;
    }

    /** Esattamente due opzioni, una pendenza ciascuna: ENTRO e OLTRE. */
    public static boolean sonoTutteSoglie(List<List<Pendenza>> opzioni) {
        return sonoCoppia(opzioni, TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO,
                TipologiaOpzionePagamento.SOLUZIONE_UNICA_OLTRE);
    }

    /** Esattamente due opzioni, una pendenza ciascuna: RIDOTTO e SCONTATO. */
    public static boolean sonoRidottoEScontato(List<List<Pendenza>> opzioni) {
        return sonoCoppia(opzioni, TipologiaOpzionePagamento.SOLUZIONE_UNICA_RIDOTTO,
                TipologiaOpzionePagamento.SOLUZIONE_UNICA_SCONTATO);
    }

    private static boolean sonoCoppia(List<List<Pendenza>> opzioni, TipologiaOpzionePagamento a,
                                      TipologiaOpzionePagamento b) {
        if (opzioni.size() != 2 || opzioni.stream().anyMatch(o -> o.size() != 1)) {
            return false;
        }
        List<TipologiaOpzionePagamento> tipologie = opzioni.stream()
                .map(o -> o.get(0).getOpzionePagamento().getTipologia()).toList();
        return tipologie.contains(a) && tipologie.contains(b);
    }

    /** Stessa scelta full/instalments/reduced_payments dell'avviso a rata unica, per un'opzione. */
    private PaymentNoticeOption toOption(List<Pendenza> pendenzeOpzione, Risolutore r) {
        PaymentNoticeOption option = new PaymentNoticeOption();
        if (pendenzeOpzione.size() == 1) {
            Pendenza unica = pendenzeOpzione.get(0);
            Dominio dominio = r.dominio(unica);
            if (isSogliaTemporale(unica)) {
                option.setReducedPayments(List.of(mapReducedPayment(unica, dominio, r.iban(unica))));
            } else {
                option.setFull(mapFullAmount(unica, dominio, r.iban(unica)));
            }
            return option;
        }
        List<Instalment> instalments = new ArrayList<>(pendenzeOpzione.size());
        for (Pendenza rata : pendenzeOpzione) {
            instalments.add(mapInstalment(rata, r.dominio(rata), r.iban(rata)));
        }
        option.setInstalments(instalments);
        return option;
    }

    /** Parte comune degli avvisi della posizione: lingua, creditore, debitore, titolo, logo. */
    private static PaymentNotice intestazione(Pendenza p, Dominio dominio, String titolo) {
        PaymentNotice notice = new PaymentNotice();
        notice.setLanguage(Languages.IT);
        notice.setCreditor(mapCreditor(dominio, pagamentoSito(dominio)));
        notice.setDebtor(mapDebtor(debitoreDi(p)));
        notice.setTitle(titolo);
        notice.setFirstLogo(firstLogoOf(dominio));
        return notice;
    }

    private static String descrizioneDi(Pendenza p) {
        return p.getOpzionePagamento().getPosizioneDebitoria().getDescrizione();
    }

    /** Stessi campi di {@link #mapFullAmount}, piu' il numero di rata. */
    private static Instalment mapInstalment(Pendenza p, Dominio dominio, IbanAccredito postale) {
        Instalment instalment = new Instalment();
        instalment.setAmount(p.getImporto());
        instalment.setNoticeNumber(p.getNumeroAvviso());
        instalment.setQrcode(qrcodeObbligatorio(p, dominio));
        LocalDate scadenza = dueDateOf(p);
        if (scadenza != null) {
            instalment.setDueDate(scadenza);
        }
        instalment.setInstalmentNumber(p.getNumeroRata());
        if (postale != null) {
            instalment.setIban(mapIban(postale));
        }
        return instalment;
    }

    /**
     * Scadenza dell'avviso: quella della pendenza e, se assente, quella della sua opzione di
     * pagamento.
     */
    static LocalDate dueDateOf(Pendenza p) {
        if (p.getDataScadenzaAvviso() != null) {
            return p.getDataScadenzaAvviso().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        }
        return p.getOpzionePagamento().getDataScadenza();
    }
}
