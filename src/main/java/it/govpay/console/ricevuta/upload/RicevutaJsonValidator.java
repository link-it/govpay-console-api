package it.govpay.console.ricevuta.upload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import it.govpay.console.ricevuta.upload.bizevents.model.CtReceiptModelResponse;
import it.govpay.console.ricevuta.upload.bizevents.model.Debtor;
import it.govpay.console.ricevuta.upload.bizevents.model.Payer;
import it.govpay.console.ricevuta.upload.bizevents.model.TransferPA;
import it.govpay.console.web.BadRequestException;

/**
 * Validazione del JSON caricato ({@code CtReceiptModelResponse}, schema
 * BizEvents), eseguita <b>prima</b> della conversione. In
 * {@code govpay-rt-batch} questo controllo non serve — il JSON arriva da
 * pagoPA ed e' per costruzione conforme e completo — ma qui lo fornisce un
 * operatore, quindi va verificato esplicitamente.
 */
@Component
public class RicevutaJsonValidator {

    /** Valori ammessi per {@code outcome} nello schema BizEvents. */
    private static final Set<String> OUTCOME_AMMESSI = Set.of("OK", "KO");

    public void valida(CtReceiptModelResponse response) {
        List<String> mancanti = new ArrayList<>();
        mancantiRadice(response, mancanti);
        mancantiAnnidati(response, mancanti);

        if (!mancanti.isEmpty()) {
            throw new BadRequestException("Campi obbligatori mancanti nella ricevuta JSON: "
                    + String.join(", ", mancanti) + ".");
        }

        // paymentDateTimeFormatted non e' fra i campi "required" dello schema BizEvents,
        // ma senza di esso core assegna silenziosamente la data di caricamento come data
        // di pagamento: va intercettato qui, esplicitamente.
        if (response.getPaymentDateTimeFormatted() == null) {
            throw new BadRequestException(
                    "Campo 'paymentDateTimeFormatted' assente: senza questo campo la data di pagamento "
                            + "verrebbe impostata silenziosamente alla data di caricamento invece che a quella "
                            + "effettiva. Il solo campo 'paymentDateTime' (data senza orario) non e' sufficiente.");
        }

        if (!OUTCOME_AMMESSI.contains(response.getOutcome())) {
            throw new BadRequestException("Valore non ammesso per 'outcome': '" + response.getOutcome()
                    + "'. Valori ammessi: " + String.join(", ", OUTCOME_AMMESSI) + ".");
        }
    }

    /** I 13 campi obbligatori di primo livello dello schema BizEvents, in ordine alfabetico. */
    private static void mancantiRadice(CtReceiptModelResponse response, List<String> mancanti) {
        richiediTesto(response.getCompanyName(), "companyName", mancanti);
        richiediTesto(response.getCreditorReferenceId(), "creditorReferenceId", mancanti);
        richiediValore(response.getDebtor(), "debtor", mancanti);
        richiediTesto(response.getDescription(), "description", mancanti);
        richiediTesto(response.getFiscalCode(), "fiscalCode", mancanti);
        richiediTesto(response.getIdChannel(), "idChannel", mancanti);
        richiediTesto(response.getIdPSP(), "idPSP", mancanti);
        richiediTesto(response.getNoticeNumber(), "noticeNumber", mancanti);
        richiediTesto(response.getOutcome(), "outcome", mancanti);
        richiediValore(response.getPaymentAmount(), "paymentAmount", mancanti);
        richiediTesto(response.getPspCompanyName(), "pspCompanyName", mancanti);
        richiediTesto(response.getReceiptId(), "receiptId", mancanti);
        richiediLista(response.getTransferList(), "transferList", mancanti);
    }

    /**
     * Campi obbligatori annidati: la sola presenza di {@code debtor}/{@code transferList}
     * non basta, i loro campi obbligatori vanno verificati anche loro — altrimenti un
     * {@code "debtor": {}} supera il controllo di primo livello e causa poi una
     * {@code NullPointerException} nel converter ({@code StEntityUniqueIdentifierType}
     * letto su un valore nullo).
     */
    private static void mancantiAnnidati(CtReceiptModelResponse response, List<String> mancanti) {
        mancantiDebtor(response.getDebtor(), mancanti);
        mancantiPayer(response.getPayer(), mancanti);
        mancantiTransferList(response.getTransferList(), mancanti);
    }

    private static void mancantiDebtor(Debtor debtor, List<String> mancanti) {
        if (debtor == null) {
            return;
        }
        mancantiSoggetto("debtor", debtor.getEntityUniqueIdentifierType(),
                debtor.getEntityUniqueIdentifierValue(), debtor.getFullName(), mancanti);
    }

    private static void mancantiPayer(Payer payer, List<String> mancanti) {
        if (payer == null) {
            return;
        }
        mancantiSoggetto("payer", payer.getEntityUniqueIdentifierType(),
                payer.getEntityUniqueIdentifierValue(), payer.getFullName(), mancanti);
    }

    private static void mancantiTransferList(List<TransferPA> transferList, List<String> mancanti) {
        if (transferList == null) {
            return;
        }
        for (int i = 0; i < transferList.size(); i++) {
            TransferPA transfer = transferList.get(i);
            // Un elemento nullo nell'array ("transferList": [null, ...]) e' JSON
            // sintatticamente valido: senza questo controllo mancantiTransfer(...)
            // solleverebbe una NullPointerException invece di un 400 parlante.
            if (transfer == null) {
                mancanti.add("transferList[" + i + "]");
            } else {
                mancantiTransfer(i, transfer, mancanti);
            }
        }
    }

    /**
     * {@code Debtor}/{@code Payer} condividono la stessa forma per i campi
     * obbligatori (tre, gli altri sono facoltativi) ma il generatore OpenAPI
     * produce un {@code EntityUniqueIdentifierTypeEnum} distinto per ciascuna
     * classe: qui accettato come {@code Object}, basta il controllo di nullita'.
     */
    private static void mancantiSoggetto(String prefisso, Object entityUniqueIdentifierType,
            String entityUniqueIdentifierValue, String fullName, List<String> mancanti) {
        if (entityUniqueIdentifierType == null) {
            mancanti.add(prefisso + ".entityUniqueIdentifierType");
        }
        if (!StringUtils.hasText(entityUniqueIdentifierValue)) {
            mancanti.add(prefisso + ".entityUniqueIdentifierValue");
        }
        if (!StringUtils.hasText(fullName)) {
            mancanti.add(prefisso + ".fullName");
        }
    }

    /**
     * {@code iban}/{@code mbdAttachment} sono annotati obbligatori dal
     * generatore ma sono mutuamente esclusivi nello schema di destinazione
     * (una voce di marca da bollo li ha entrambi vuoti tranne uno): non
     * vengono verificati qui, li' governa il controllo dedicato in
     * {@link CtReceiptV2Converter#hasTransferSenzaIbanEMbdAttachment}.
     *
     * <p>{@code idTransfer} e' invece verificato qui pur essendo
     * {@code @Nullable} nel model BizEvents: lo schema di <b>destinazione</b>
     * ({@code ctTransferPAReceiptV2} in {@code paForNode.xsd}) lo richiede
     * senza {@code minOccurs="0"} — un valore assente non produce un fault
     * SOAP parlante ma una {@code NullPointerException} nel converter
     * ({@code CtTransferPAReceiptV2.setIdTransfer} auto-unboxing un
     * {@code Integer} nullo), quindi va intercettato qui come gli altri
     * campi obbligatori.
     */
    private static void mancantiTransfer(int indice, TransferPA transfer, List<String> mancanti) {
        String prefisso = "transferList[" + indice + "]";
        richiediValore(transfer.getIdTransfer(), prefisso + ".idTransfer", mancanti);
        richiediValore(transfer.getTransferAmount(), prefisso + ".transferAmount", mancanti);
        richiediTesto(transfer.getFiscalCodePA(), prefisso + ".fiscalCodePA", mancanti);
        richiediTesto(transfer.getRemittanceInformation(), prefisso + ".remittanceInformation", mancanti);
        richiediTesto(transfer.getTransferCategory(), prefisso + ".transferCategory", mancanti);
    }

    // I tre predicati sotto ricevono il valore gia' estratto invece di interrogare il
    // model: i getter generati da bizEvents.yaml sono annotati @Nonnull per i campi
    // "required" dello schema, ma e' la descrizione del contratto, non una garanzia
    // sull'oggetto deserializzato — Jackson lascia il campo a null quando il JSON
    // caricato dall'operatore lo omette, ed e' esattamente il caso che questo
    // validatore esiste per intercettare. Passando il valore per parametro il
    // controllo resta dov'e' utile senza leggersi come una condizione impossibile.

    /** Il campo e' obbligatorio: assente se {@code null}. */
    private static void richiediValore(Object valore, String campo, List<String> mancanti) {
        if (valore == null) {
            mancanti.add(campo);
        }
    }

    /** Il campo e' obbligatorio e non puo' essere vuoto o di soli spazi. */
    private static void richiediTesto(String valore, String campo, List<String> mancanti) {
        if (!StringUtils.hasText(valore)) {
            mancanti.add(campo);
        }
    }

    /** Il campo e' obbligatorio e deve contenere almeno un elemento. */
    private static void richiediLista(Collection<?> valore, String campo, List<String> mancanti) {
        if (valore == null || valore.isEmpty()) {
            mancanti.add(campo);
        }
    }
}
