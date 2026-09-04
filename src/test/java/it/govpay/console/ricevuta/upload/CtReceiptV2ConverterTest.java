package it.govpay.console.ricevuta.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.gov.pagopa.pagopa_api.pa.pafornode.CtReceiptV2;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtTransferPAReceiptV2;
import it.govpay.console.ricevuta.upload.bizevents.model.CtReceiptModelResponse;
import it.govpay.console.ricevuta.upload.bizevents.model.Debtor;
import it.govpay.console.ricevuta.upload.bizevents.model.TransferPA;
import it.govpay.console.web.BadRequestException;

class CtReceiptV2ConverterTest {

    private static CtReceiptModelResponse rispostaConTransfer(TransferPA transfer) {
        return new CtReceiptModelResponse()
                .receiptId("receipt-1")
                .noticeNumber("311000000000000000")
                .fiscalCode("77777777777")
                .outcome("OK")
                .creditorReferenceId("iuv123")
                .paymentAmount(new BigDecimal("10.00"))
                .description("Pagamento TARI")
                .companyName("Comune di Prova")
                .debtor(new Debtor()
                        .entityUniqueIdentifierType(Debtor.EntityUniqueIdentifierTypeEnum.F)
                        .entityUniqueIdentifierValue("RSSMRA80A01H501U")
                        .fullName("Mario Rossi"))
                .transferList(List.of(transfer))
                .idPSP("psp-1")
                .pspCompanyName("PSP di Prova")
                .idChannel("channel-1");
    }

    /**
     * Il contenuto di {@code mbdAttachment} nel JSON e' gia' testo Base64
     * (finira' cosi' com'e' nel campo XSD {@code xsd:base64Binary} di
     * destinazione): il converter deve decodificarlo, non trattarlo come
     * byte grezzi da ricodificare — altrimenti l'allegato che arriva a
     * api-pagopa e' Base64 applicato due volte, diverso da quello originale.
     */
    @Test
    void mbdAttachmentVieneDecodificatoNonRicodificato() {
        byte[] contenutoOriginale = "contenuto marca da bollo".getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(contenutoOriginale);

        TransferPA transfer = new TransferPA()
                .idTransfer(1)
                .transferAmount(new BigDecimal("16.00"))
                .fiscalCodePA("77777777777")
                .mbdAttachment(base64)
                .remittanceInformation("Marca da bollo")
                .transferCategory("9/9/");

        CtReceiptV2 receipt = CtReceiptV2Converter.toCtReceiptV2(rispostaConTransfer(transfer));
        CtTransferPAReceiptV2 ctTransfer = receipt.getTransferList().getTransfers().get(0);

        assertThat(ctTransfer.getMBDAttachment()).isEqualTo(contenutoOriginale);
    }

    @Test
    void mbdAttachmentNonBase64ValidoRitornaBadRequest() {
        TransferPA transfer = new TransferPA()
                .idTransfer(1)
                .transferAmount(new BigDecimal("16.00"))
                .fiscalCodePA("77777777777")
                .mbdAttachment("non e' base64 valido!!!")
                .remittanceInformation("Marca da bollo")
                .transferCategory("9/9/");

        assertThatThrownBy(() -> CtReceiptV2Converter.toCtReceiptV2(rispostaConTransfer(transfer)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mbdAttachment");
    }

    /**
     * {@code "metadata": [null]} e' JSON sintatticamente valido: senza un
     * controllo dedicato causava una NullPointerException invece di un 400
     * parlante.
     */
    @Test
    void elementoNulloInMetadataRitornaBadRequestNonNullPointer() {
        List<it.govpay.console.ricevuta.upload.bizevents.model.MapEntry> metadataConNullo = new java.util.ArrayList<>();
        metadataConNullo.add(null);

        CtReceiptModelResponse response = rispostaConTransfer(new TransferPA()
                .idTransfer(1)
                .transferAmount(new BigDecimal("10.00"))
                .fiscalCodePA("77777777777")
                .iban("IT60X0542811101000000123456")
                .remittanceInformation("TARI saldo")
                .transferCategory("9/0101108TS/"))
                .metadata(metadataConNullo);

        assertThatThrownBy(() -> CtReceiptV2Converter.toCtReceiptV2(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void ibanVieneCopiatoVerbatim() {
        TransferPA transfer = new TransferPA()
                .idTransfer(1)
                .transferAmount(new BigDecimal("10.00"))
                .fiscalCodePA("77777777777")
                .iban("IT60X0542811101000000123456")
                .remittanceInformation("TARI saldo")
                .transferCategory("9/0101108TS/");

        CtReceiptV2 receipt = CtReceiptV2Converter.toCtReceiptV2(rispostaConTransfer(transfer));
        CtTransferPAReceiptV2 ctTransfer = receipt.getTransferList().getTransfers().get(0);

        assertThat(ctTransfer.getIBAN()).isEqualTo("IT60X0542811101000000123456");
        assertThat(ctTransfer.getMBDAttachment()).isNull();
    }
}
