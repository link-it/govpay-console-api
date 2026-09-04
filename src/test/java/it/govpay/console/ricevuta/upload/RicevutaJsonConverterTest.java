package it.govpay.console.ricevuta.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Intermediario;
import it.govpay.console.entity.Stazione;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.UnprocessableEntityException;

class RicevutaJsonConverterTest {

    private static final String COD_DOMINIO = "77777777777";

    private static String jsonRicevuta(String ibanTransfer, String mbdAttachment) {
        return """
                {
                  "receiptId": "receipt-1",
                  "noticeNumber": "311000000000000000",
                  "fiscalCode": "%s",
                  "outcome": "OK",
                  "creditorReferenceId": "iuv123",
                  "paymentAmount": 10.00,
                  "description": "Pagamento TARI",
                  "companyName": "Comune di Prova",
                  "debtor": {
                    "fullName": "Mario Rossi",
                    "entityUniqueIdentifierType": "F",
                    "entityUniqueIdentifierValue": "RSSMRA80A01H501U"
                  },
                  "transferList": [
                    {
                      "idTransfer": "1",
                      "fiscalCodePA": "%s",
                      "iban": %s,
                      "mbdAttachment": %s,
                      "remittanceInformation": "causale",
                      "transferAmount": 10.00,
                      "transferCategory": "cat"
                    }
                  ],
                  "idPSP": "psp-1",
                  "pspCompanyName": "PSP di Prova",
                  "idChannel": "channel-1",
                  "paymentDateTimeFormatted": "2026-09-01T10:00:00.000+02:00"
                }
                """.formatted(COD_DOMINIO, COD_DOMINIO,
                ibanTransfer == null ? "null" : "\"" + ibanTransfer + "\"",
                mbdAttachment == null ? "null" : "\"" + mbdAttachment + "\"");
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static Dominio dominioConStazione() {
        Intermediario intermediario = new Intermediario();
        intermediario.setCodIntermediario("intermediario1");
        Stazione stazione = new Stazione();
        stazione.setCodStazione("stazione1");
        stazione.setIntermediario(intermediario);
        Dominio dominio = new Dominio();
        dominio.setCodDominio(COD_DOMINIO);
        dominio.setStazione(stazione);
        return dominio;
    }

    private RicevutaJsonConverter converter(DominioRepository dominioRepository) {
        return new RicevutaJsonConverter(new ObjectMapper(), new RicevutaJsonValidator(), dominioRepository);
    }

    @Test
    void ricevutaValidaVieneConvertitaERisoltaViaDominioStazioneIntermediario() {
        DominioRepository dominioRepository = mock(DominioRepository.class);
        when(dominioRepository.findByCodDominio(COD_DOMINIO)).thenReturn(Optional.of(dominioConStazione()));

        RicevutaJsonConversione conversione = converter(dominioRepository).convert(bytes(jsonRicevuta("IT60X0542811101000000123456", null)));
        PaSendRTV2Request request = conversione.request();

        assertThat(request.getIdPA()).isEqualTo(COD_DOMINIO);
        assertThat(request.getIdStation()).isEqualTo("stazione1");
        assertThat(request.getIdBrokerPA()).isEqualTo("intermediario1");
        assertThat(request.getReceipt().getReceiptId()).isEqualTo("receipt-1");
    }

    @Test
    void dominioNonCensitoRitornaBadRequest() {
        DominioRepository dominioRepository = mock(DominioRepository.class);
        when(dominioRepository.findByCodDominio(COD_DOMINIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter(dominioRepository).convert(bytes(jsonRicevuta("IT60X0542811101000000123456", null))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("non censito");
    }

    @Test
    void dominioSenzaStazioneRitornaBadRequestDistinto() {
        DominioRepository dominioRepository = mock(DominioRepository.class);
        Dominio dominio = new Dominio();
        dominio.setCodDominio(COD_DOMINIO);
        when(dominioRepository.findByCodDominio(COD_DOMINIO)).thenReturn(Optional.of(dominio));

        assertThatThrownBy(() -> converter(dominioRepository).convert(bytes(jsonRicevuta("IT60X0542811101000000123456", null))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("stazione");
    }

    @Test
    void jsonNonConformeAlModelRitornaUnprocessableEntity() {
        DominioRepository dominioRepository = mock(DominioRepository.class);
        assertThatThrownBy(() -> converter(dominioRepository).convert(bytes("{\"paymentAmount\": \"non-e-un-numero\"}")))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    /**
     * {@code null} e' JSON sintatticamente valido: Jackson lo deserializza a
     * {@code null} senza sollevare eccezioni, non intercettato dal catch su
     * {@code JacksonException}. Senza un controllo dedicato la validazione
     * successiva (`response.getCompanyName()`) sollevava una
     * NullPointerException invece di un 422 parlante.
     */
    @Test
    void jsonNullRitornaUnprocessableEntityNonNullPointer() {
        DominioRepository dominioRepository = mock(DominioRepository.class);
        assertThatThrownBy(() -> converter(dominioRepository).convert(bytes("null")))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void voceMbtSenzaIbanESenzaAllegatoRitornaUnprocessableEntity() {
        DominioRepository dominioRepository = mock(DominioRepository.class);
        when(dominioRepository.findByCodDominio(COD_DOMINIO)).thenReturn(Optional.of(dominioConStazione()));

        assertThatThrownBy(() -> converter(dominioRepository).convert(bytes(jsonRicevuta(null, null))))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("mbdAttachment");
    }

    @Test
    void voceMbtConIbanVuotoContaComeAssenteERitornaUnprocessableEntity() {
        DominioRepository dominioRepository = mock(DominioRepository.class);
        when(dominioRepository.findByCodDominio(COD_DOMINIO)).thenReturn(Optional.of(dominioConStazione()));

        assertThatThrownBy(() -> converter(dominioRepository).convert(bytes(jsonRicevuta("", ""))))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("mbdAttachment");
    }

    @Test
    void voceMbtConAllegatoValorizzatoPassa() {
        DominioRepository dominioRepository = mock(DominioRepository.class);
        when(dominioRepository.findByCodDominio(COD_DOMINIO)).thenReturn(Optional.of(dominioConStazione()));

        RicevutaJsonConversione conversione = converter(dominioRepository).convert(bytes(jsonRicevuta(null, "YWxsZWdhdG8=")));
        PaSendRTV2Request request = conversione.request();

        assertThat(request.getReceipt().getTransferList().getTransfers()).hasSize(1);
        assertThat(request.getReceipt().getTransferList().getTransfers().get(0).getMBDAttachment()).isNotNull();
    }
}
