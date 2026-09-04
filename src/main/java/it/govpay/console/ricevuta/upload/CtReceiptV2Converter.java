package it.govpay.console.ricevuta.upload;

import java.util.Base64;
import java.util.List;

import org.springframework.util.StringUtils;

import it.gov.pagopa.pagopa_api.pa.pafornode.CtEntityUniqueIdentifier;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtMapEntry;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtMetadata;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtReceiptV2;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtSubject;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtTransferListPAReceiptV2;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtTransferPAReceiptV2;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.StEntityUniqueIdentifierType;
import it.gov.pagopa.pagopa_api.pa.pafornode.StOutcome;
import it.govpay.console.ricevuta.upload.bizevents.model.CtReceiptModelResponse;
import it.govpay.console.ricevuta.upload.bizevents.model.Debtor;
import it.govpay.console.ricevuta.upload.bizevents.model.MapEntry;
import it.govpay.console.ricevuta.upload.bizevents.model.Payer;
import it.govpay.console.ricevuta.upload.bizevents.model.TransferPA;
import it.govpay.console.web.BadRequestException;

/**
 * Conversione da modello BizEvents (JSON, schema {@code bizEvents.yaml}) a
 * {@link PaSendRTV2Request} (XSD {@code paForNode.xsd}), per il ramo JSON del
 * caricamento ricevuta da cruscotto.
 *
 * <p>Porto da {@code CtReceiptV2Converter} in {@code govpay-rt-batch}
 * (stesso schema BizEvents, stesso XSD di destinazione), commit
 * {@code 0d300ea5a6f2c4f689ed1e68f42e60d0cbbae326} del 2026-09-03, con due
 * adattamenti specifici di console-api:
 * <ul>
 *   <li>il fix di simmetria {@code hasText} su IBAN/MBDAttachment
 *       (vedi {@link #hasTransferSenzaIbanEMbdAttachment}) e' gia' applicato
 *       qui, non nella forma originale col bug asimmetrico;</li>
 *   <li>{@code paymentDateTime}: il binding JAXB di questo progetto mappa
 *       {@code xsd:dateTime} su {@link java.time.LocalDateTime} (adapter
 *       {@code DateTimeAdapter}, vedi {@code global.xjb}), non su
 *       {@link java.time.OffsetDateTime} come in govpay-rt-batch — la
 *       conversione usa {@code OffsetDateTime.toLocalDateTime()}, stessa
 *       semantica (solo troncamento dell'offset) gia' usata da
 *       {@code DataTypeAdapterCXF.parseLocalDateTime} per lo stesso campo
 *       quando arriva dal Nodo.</li>
 * </ul>
 *
 * <p><b>Perdite note della conversione</b>:
 * {@code standIn} e' forzato a {@code false}; {@code paymentNote} non esiste
 * nel model JSON e non viene quindi valorizzato sul {@link CtReceiptV2}
 * risultante.
 *
 * <p><b>TODO</b>: questa classe e i model generati da {@code bizEvents.yaml}
 * sono duplicati identici fra {@code console-api} e {@code govpay-rt-batch}
 * (costo esplicito: "due copie che possono divergere, e la
 * divergenza sarebbe silenziosa").
 */
public final class CtReceiptV2Converter {

    private CtReceiptV2Converter() {
    }

    public static PaSendRTV2Request toPaSendRTV2Request(String codIntermediario, String codStazione,
            String codDominio, CtReceiptModelResponse response) {
        PaSendRTV2Request paSendRTV2Request = new PaSendRTV2Request();

        paSendRTV2Request.setIdBrokerPA(codIntermediario);
        paSendRTV2Request.setIdStation(codStazione);
        paSendRTV2Request.setIdPA(codDominio);
        paSendRTV2Request.setReceipt(toCtReceiptV2(response));

        return paSendRTV2Request;
    }

    /**
     * Discriminante deterministico per il bug pagoPA: {@code paForNode.xsd}
     * rende {@code IBAN}/{@code MBDAttachment} mutuamente esclusivi e
     * obbligatori uno dei due ({@code <xsd:choice>} senza
     * {@code minOccurs="0"}), quindi un transfer senza IBAN e' per
     * costruzione una marca da bollo — se manca anche {@code MBDAttachment},
     * pagoPA non ha restituito l'allegato e la RT non e' acquisibile cosi'
     * com'e' (verrebbe inviata a paForNode priva sia di IBAN sia di
     * allegato, violando la choice). Va chiamato <b>dopo</b>
     * {@link #toCtTransferListPAReceiptV2}, che gia' applica {@code hasText}
     * in modo simmetrico su entrambi i campi: qui basta il controllo di
     * nullita' sui valori gia' convertiti.
     */
    public static boolean hasTransferSenzaIbanEMbdAttachment(CtReceiptV2 receipt) {
        if (receipt == null || receipt.getTransferList() == null) {
            return false;
        }
        for (CtTransferPAReceiptV2 transfer : receipt.getTransferList().getTransfers()) {
            if (transfer.getIBAN() == null && transfer.getMBDAttachment() == null) {
                return true;
            }
        }
        return false;
    }

    public static CtReceiptV2 toCtReceiptV2(CtReceiptModelResponse response) {
        CtReceiptV2 ctReceiptV2 = new CtReceiptV2();

        ctReceiptV2.setStandIn(false);
        ctReceiptV2.setApplicationDate(response.getApplicationDate());
        ctReceiptV2.setChannelDescription(response.getChannelDescription());
        ctReceiptV2.setCompanyName(response.getCompanyName());
        ctReceiptV2.setCreditorReferenceId(response.getCreditorReferenceId());
        ctReceiptV2.setDebtor(toCtSubjectDebtor(response.getDebtor()));
        ctReceiptV2.setDescription(response.getDescription());
        ctReceiptV2.setFee(response.getFee());
        ctReceiptV2.setFiscalCode(response.getFiscalCode());
        ctReceiptV2.setIdBundle(response.getIdBundle());
        ctReceiptV2.setIdChannel(response.getIdChannel());
        ctReceiptV2.setIdCiBundle(response.getIdCiBundle());
        ctReceiptV2.setIdPSP(response.getIdPSP());
        ctReceiptV2.setMetadata(toCtReceiptV2Metadata(response.getMetadata()));
        ctReceiptV2.setNoticeNumber(response.getNoticeNumber());
        ctReceiptV2.setOfficeName(response.getOfficeName());
        ctReceiptV2.setOutcome(StOutcome.fromValue(response.getOutcome()));
        ctReceiptV2.setPayer(toCtSubjectPayer(response.getPayer()));
        ctReceiptV2.setPaymentAmount(response.getPaymentAmount());
        ctReceiptV2.setPaymentDateTime(response.getPaymentDateTimeFormatted() != null
                ? response.getPaymentDateTimeFormatted().toLocalDateTime()
                : null);
        ctReceiptV2.setPaymentMethod(response.getPaymentMethod());
        ctReceiptV2.setPrimaryCiIncurredFee(response.getPrimaryCiIncurredFee());
        ctReceiptV2.setPSPCompanyName(response.getPspCompanyName());
        ctReceiptV2.setPspFiscalCode(response.getPspFiscalCode());
        ctReceiptV2.setPspPartitaIVA(response.getPspPartitaIVA());
        ctReceiptV2.setReceiptId(response.getReceiptId());
        ctReceiptV2.setTransferDate(response.getTransferDate());
        ctReceiptV2.setTransferList(toCtTransferListPAReceiptV2(response.getTransferList()));

        return ctReceiptV2;
    }

    private static CtTransferListPAReceiptV2 toCtTransferListPAReceiptV2(List<TransferPA> transferList) {
        if (transferList == null) {
            return null;
        }

        CtTransferListPAReceiptV2 ctTransferListPAReceiptV2 = new CtTransferListPAReceiptV2();

        for (TransferPA transferPA : transferList) {

            CtTransferPAReceiptV2 ctTransferPAReceiptV2 = new CtTransferPAReceiptV2();

            ctTransferPAReceiptV2.setFiscalCodePA(transferPA.getFiscalCodePA());
            ctTransferPAReceiptV2.setIdTransfer(transferPA.getIdTransfer());
            // getIban()/getMbdAttachment() sono annotati @Nonnull dal generatore OpenAPI
            // (lo schema BizEvents li dichiara entrambi required), quindi il solo controllo
            // di nullita' non discrimina mai: pagoPA manda una stringa vuota per il campo
            // che non si applica alla voce (iban per una marca da bollo, mbdAttachment per
            // un'entrata). paForNode.xsd rende i due campi mutuamente esclusivi
            // (<xsd:choice>): un setIBAN("") incondizionato soddisfarebbe la choice
            // mentendo (voce che sembra un'entrata con IBAN vuoto invece di una marca da
            // bollo). Simmetrico su entrambi i campi, hasText() copre null e stringa vuota/blank.
            if (StringUtils.hasText(transferPA.getIban())) {
                ctTransferPAReceiptV2.setIBAN(transferPA.getIban());
            }
            if (StringUtils.hasText(transferPA.getMbdAttachment())) {
                // mbdAttachment nel JSON e' gia' testo Base64 (e' cosi' che finira' nel
                // campo XSD xsd:base64Binary di destinazione): un .getBytes() qui
                // codificherebbe in Base64 il testo Base64 stesso, corrompendo
                // l'allegato che arriva a api-pagopa. Va decodificato, non riletto
                // come byte grezzi.
                ctTransferPAReceiptV2.setMBDAttachment(decodeBase64Attachment(transferPA.getMbdAttachment()));
            }
            ctTransferPAReceiptV2.setMetadata(toCtReceiptV2Metadata(transferPA.getMetadata()));
            ctTransferPAReceiptV2.setRemittanceInformation(transferPA.getRemittanceInformation());
            ctTransferPAReceiptV2.setTransferAmount(transferPA.getTransferAmount());
            ctTransferPAReceiptV2.setTransferCategory(transferPA.getTransferCategory());

            ctTransferListPAReceiptV2.getTransfers().add(ctTransferPAReceiptV2);
        }

        return ctTransferListPAReceiptV2;
    }

    private static byte[] decodeBase64Attachment(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Campo 'mbdAttachment' non e' un Base64 valido.");
        }
    }

    private static CtMetadata toCtReceiptV2Metadata(List<MapEntry> metadata) {
        if (metadata == null) {
            return null;
        }

        CtMetadata ctMetadata = new CtMetadata();

        for (MapEntry mapEntry : metadata) {
            // Un elemento nullo ("metadata": [null, ...]) e' JSON sintatticamente
            // valido: senza questo controllo il getKey() sotto solleverebbe una
            // NullPointerException invece di un errore parlante.
            if (mapEntry == null) {
                throw new BadRequestException("Elemento nullo in 'metadata'.");
            }
            CtMapEntry ctMapEntry = new CtMapEntry();

            ctMapEntry.setKey(mapEntry.getKey());
            ctMapEntry.setValue(mapEntry.getValue());

            ctMetadata.getMapEntries().add(ctMapEntry);
        }

        return ctMetadata;
    }

    private static CtSubject toCtSubjectDebtor(Debtor debtor) {
        if (debtor == null) {
            return null;
        }

        CtSubject ctSubject = new CtSubject();

        ctSubject.setCity(debtor.getCity());
        ctSubject.setCivicNumber(debtor.getCivicNumber());
        ctSubject.setCountry(debtor.getCountry());
        ctSubject.setEMail(debtor.getEmail());
        ctSubject.setFullName(debtor.getFullName());
        ctSubject.setPostalCode(debtor.getPostalCode());
        ctSubject.setStateProvinceRegion(debtor.getStateProvinceRegion());
        ctSubject.setStreetName(debtor.getStreetName());
        CtEntityUniqueIdentifier uniqueIdentifier = new CtEntityUniqueIdentifier();
        if (debtor.getEntityUniqueIdentifierType().toString().equals(StEntityUniqueIdentifierType.F.toString())) {
            uniqueIdentifier.setEntityUniqueIdentifierType(StEntityUniqueIdentifierType.F);
        } else {
            uniqueIdentifier.setEntityUniqueIdentifierType(StEntityUniqueIdentifierType.G);
        }
        uniqueIdentifier.setEntityUniqueIdentifierValue(debtor.getEntityUniqueIdentifierValue());
        ctSubject.setUniqueIdentifier(uniqueIdentifier);

        return ctSubject;
    }

    private static CtSubject toCtSubjectPayer(Payer payer) {
        if (payer == null) {
            return null;
        }

        CtSubject ctSubject = new CtSubject();

        ctSubject.setCity(payer.getCity());
        ctSubject.setCivicNumber(payer.getCivicNumber());
        ctSubject.setCountry(payer.getCountry());
        ctSubject.setEMail(payer.getEmail());
        ctSubject.setFullName(payer.getFullName());
        ctSubject.setPostalCode(payer.getPostalCode());
        ctSubject.setStateProvinceRegion(payer.getStateProvinceRegion());
        ctSubject.setStreetName(payer.getStreetName());
        CtEntityUniqueIdentifier uniqueIdentifier = new CtEntityUniqueIdentifier();
        if (payer.getEntityUniqueIdentifierType().toString().equals(StEntityUniqueIdentifierType.F.toString())) {
            uniqueIdentifier.setEntityUniqueIdentifierType(StEntityUniqueIdentifierType.F);
        } else {
            uniqueIdentifier.setEntityUniqueIdentifierType(StEntityUniqueIdentifierType.G);
        }
        uniqueIdentifier.setEntityUniqueIdentifierValue(payer.getEntityUniqueIdentifierValue());
        ctSubject.setUniqueIdentifier(uniqueIdentifier);

        return ctSubject;
    }
}
