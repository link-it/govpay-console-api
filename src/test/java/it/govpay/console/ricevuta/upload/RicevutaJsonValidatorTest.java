package it.govpay.console.ricevuta.upload;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.govpay.console.ricevuta.upload.bizevents.model.CtReceiptModelResponse;
import it.govpay.console.ricevuta.upload.bizevents.model.Debtor;
import it.govpay.console.ricevuta.upload.bizevents.model.TransferPA;
import it.govpay.console.web.BadRequestException;

class RicevutaJsonValidatorTest {

    private final RicevutaJsonValidator validator = new RicevutaJsonValidator();

    private static Debtor debtorValido() {
        return new Debtor()
                .entityUniqueIdentifierType(Debtor.EntityUniqueIdentifierTypeEnum.F)
                .entityUniqueIdentifierValue("RSSMRA80A01H501U")
                .fullName("Mario Rossi");
    }

    private static TransferPA transferValido() {
        return new TransferPA()
                .idTransfer(1)
                .transferAmount(new BigDecimal("10.00"))
                .fiscalCodePA("77777777777")
                .iban("IT60X0542811101000000123456")
                .remittanceInformation("TARI saldo")
                .transferCategory("9/0101108TS/");
    }

    private static CtReceiptModelResponse rispostaValida() {
        return new CtReceiptModelResponse()
                .receiptId("receipt-1")
                .noticeNumber("311000000000000000")
                .fiscalCode("77777777777")
                .outcome("OK")
                .creditorReferenceId("iuv123")
                .paymentAmount(new BigDecimal("10.00"))
                .description("Pagamento TARI")
                .companyName("Comune di Prova")
                .debtor(debtorValido())
                .transferList(List.of(transferValido()))
                .idPSP("psp-1")
                .pspCompanyName("PSP di Prova")
                .idChannel("channel-1")
                .paymentDateTimeFormatted(OffsetDateTime.now());
    }

    @Test
    void rispostaCompletaPassaLaValidazione() {
        assertThatCode(() -> validator.valida(rispostaValida())).doesNotThrowAnyException();
    }

    @Test
    void campoObbligatorioMancanteVieneElencato() {
        CtReceiptModelResponse response = rispostaValida().companyName(null);
        assertThatThrownBy(() -> validator.valida(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("companyName");
    }

    @Test
    void piuCampiMancantiVengonoElencatiTutti() {
        CtReceiptModelResponse response = rispostaValida().companyName(null).idPSP(null);
        assertThatThrownBy(() -> validator.valida(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("companyName")
                .hasMessageContaining("idPSP");
    }

    @Test
    void transferListVuotaEQuivaleAMancante() {
        CtReceiptModelResponse response = rispostaValida().transferList(List.of());
        assertThatThrownBy(() -> validator.valida(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transferList");
    }

    @Test
    void paymentDateTimeFormattedAssenteRitornaBadRequestParlante() {
        CtReceiptModelResponse response = rispostaValida().paymentDateTimeFormatted(null);
        assertThatThrownBy(() -> validator.valida(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("paymentDateTimeFormatted");
    }

    @Test
    void outcomeNonAmmessoRitornaBadRequest() {
        CtReceiptModelResponse response = rispostaValida().outcome("BOH");
        assertThatThrownBy(() -> validator.valida(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("outcome");
    }

    /**
     * Un {@code debtor: {}} supera il solo controllo "presente" e prima di
     * questo fix arrivava al converter, che lancia una
     * {@code NullPointerException} leggendo {@code entityUniqueIdentifierType}
     * (500 invece di 400).
     */
    @Test
    void debtorVuotoElencaICampiObbligatoriMancanti() {
        CtReceiptModelResponse response = rispostaValida().debtor(new Debtor());
        assertThatThrownBy(() -> validator.valida(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("debtor.entityUniqueIdentifierType")
                .hasMessageContaining("debtor.entityUniqueIdentifierValue")
                .hasMessageContaining("debtor.fullName");
    }

    /**
     * {@code "transferList": [null]} e' JSON sintatticamente valido:
     * senza un controllo dedicato causava una NullPointerException invece
     * di un 400 parlante.
     */
    @Test
    void elementoNulloInTransferListRitornaBadRequestNonNullPointer() {
        List<TransferPA> conNullo = new java.util.ArrayList<>();
        conNullo.add(null);
        CtReceiptModelResponse response = rispostaValida().transferList(conNullo);
        assertThatThrownBy(() -> validator.valida(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transferList[0]");
    }

    @Test
    void transferVuotoElencaICampiObbligatoriMancantiSenzaRichiedereIbanOMbdAttachment() {
        CtReceiptModelResponse response = rispostaValida().transferList(List.of(new TransferPA()));
        assertThatThrownBy(() -> validator.valida(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transferList[0].transferAmount")
                .hasMessageContaining("transferList[0].fiscalCodePA")
                .hasMessageContaining("transferList[0].remittanceInformation")
                .hasMessageContaining("transferList[0].transferCategory")
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
                        .doesNotContain("transferList[0].iban")
                        .doesNotContain("transferList[0].mbdAttachment"));
    }
}
