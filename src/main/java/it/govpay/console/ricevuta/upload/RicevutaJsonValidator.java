package it.govpay.console.ricevuta.upload;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import it.govpay.console.ricevuta.upload.bizevents.model.CtReceiptModelResponse;
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

    /** I 13 campi obbligatori dello schema BizEvents, in ordine alfabetico. */
    private static final Set<String> OUTCOME_AMMESSI = Set.of("OK", "KO");

    public void valida(CtReceiptModelResponse response) {
        List<String> mancanti = new ArrayList<>();
        if (!StringUtils.hasText(response.getCompanyName())) {
            mancanti.add("companyName");
        }
        if (!StringUtils.hasText(response.getCreditorReferenceId())) {
            mancanti.add("creditorReferenceId");
        }
        if (response.getDebtor() == null) {
            mancanti.add("debtor");
        }
        if (!StringUtils.hasText(response.getDescription())) {
            mancanti.add("description");
        }
        if (!StringUtils.hasText(response.getFiscalCode())) {
            mancanti.add("fiscalCode");
        }
        if (!StringUtils.hasText(response.getIdChannel())) {
            mancanti.add("idChannel");
        }
        if (!StringUtils.hasText(response.getIdPSP())) {
            mancanti.add("idPSP");
        }
        if (!StringUtils.hasText(response.getNoticeNumber())) {
            mancanti.add("noticeNumber");
        }
        if (!StringUtils.hasText(response.getOutcome())) {
            mancanti.add("outcome");
        }
        if (response.getPaymentAmount() == null) {
            mancanti.add("paymentAmount");
        }
        if (!StringUtils.hasText(response.getPspCompanyName())) {
            mancanti.add("pspCompanyName");
        }
        if (!StringUtils.hasText(response.getReceiptId())) {
            mancanti.add("receiptId");
        }
        if (response.getTransferList() == null || response.getTransferList().isEmpty()) {
            mancanti.add("transferList");
        }

        // Campi obbligatori annidati: la sola presenza di "debtor"/"transferList" non
        // basta, i loro campi obbligatori vanno verificati anche loro — altrimenti un
        // "debtor": {} supera questo controllo e causa poi una NullPointerException
        // nel converter (StEntityUniqueIdentifierType letto su un valore nullo).
        if (response.getDebtor() != null) {
            mancantiSoggetto("debtor", response.getDebtor().getEntityUniqueIdentifierType(),
                    response.getDebtor().getEntityUniqueIdentifierValue(), response.getDebtor().getFullName(),
                    mancanti);
        }
        if (response.getPayer() != null) {
            mancantiSoggetto("payer", response.getPayer().getEntityUniqueIdentifierType(),
                    response.getPayer().getEntityUniqueIdentifierValue(), response.getPayer().getFullName(),
                    mancanti);
        }
        if (response.getTransferList() != null) {
            List<TransferPA> transferList = response.getTransferList();
            for (int i = 0; i < transferList.size(); i++) {
                TransferPA transfer = transferList.get(i);
                // Un elemento nullo nell'array ("transferList": [null, ...]) e' JSON
                // sintatticamente valido: senza questo controllo mancantiTransfer(...)
                // solleverebbe una NullPointerException invece di un 400 parlante.
                if (transfer == null) {
                    mancanti.add("transferList[" + i + "]");
                    continue;
                }
                mancantiTransfer(i, transfer, mancanti);
            }
        }

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
        if (transfer.getIdTransfer() == null) {
            mancanti.add(prefisso + ".idTransfer");
        }
        if (transfer.getTransferAmount() == null) {
            mancanti.add(prefisso + ".transferAmount");
        }
        if (!StringUtils.hasText(transfer.getFiscalCodePA())) {
            mancanti.add(prefisso + ".fiscalCodePA");
        }
        if (!StringUtils.hasText(transfer.getRemittanceInformation())) {
            mancanti.add(prefisso + ".remittanceInformation");
        }
        if (!StringUtils.hasText(transfer.getTransferCategory())) {
            mancanti.add(prefisso + ".transferCategory");
        }
    }
}
