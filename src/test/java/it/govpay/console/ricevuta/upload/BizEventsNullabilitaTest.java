package it.govpay.console.ricevuta.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import it.govpay.console.ricevuta.upload.bizevents.model.CtReceiptModelResponse;
import it.govpay.console.web.BadRequestException;
import tools.jackson.databind.ObjectMapper;

/**
 * Fissa il presupposto su cui poggia {@link RicevutaJsonValidator}: i campi
 * {@code required} dello schema BizEvents assenti dal JSON arrivano a
 * {@code null} sull'oggetto deserializzato, invece di far fallire la
 * deserializzazione.
 *
 * <p>Il generatore OpenAPI annota quei getter {@code @jakarta.annotation.Nonnull}
 * e vi mette {@code @JsonProperty(required = true)}, ma per Jackson sono
 * metadati di schema, non vincoli di deserializzazione: e' proprio questo che
 * lascia al validator il compito di intercettare i campi mancanti e di
 * rispondere con un 400 che li elenca, invece di un errore di parsing opaco.
 * (Ed e' anche il motivo per cui Sonar legge quei controlli come condizioni
 * impossibili, vedi i metodi {@code richiedi*} del validator.)
 *
 * <p>Il presupposto e' gia' verificato di fatto dal percorso completo
 * ({@code RicevutaUploadIntegrationTest.jsonConCampiMancantiRitorna400ChiElencaICampi}),
 * ma solo di rimbalzo: se un aggiornamento di Jackson cambiasse questo
 * comportamento, li' il sintomo sarebbe un caricamento RT che fallisce in modo
 * strano. Qui invece il fallimento punta dritto alla causa.
 */
class BizEventsNullabilitaTest {

    /** JSON valido ma privo di <b>tutti</b> i campi obbligatori dello schema. */
    private static final String SENZA_CAMPI_OBBLIGATORI = "{\"receiptId\":\"RT1\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void campiRequiredAssentiNonFannoFallireLaDeserializzazione() {
        assertThatCode(() -> objectMapper.readValue(SENZA_CAMPI_OBBLIGATORI, CtReceiptModelResponse.class))
                .doesNotThrowAnyException();
    }

    @Test
    void campiRequiredAssentiRestanoNullNonostanteLAnnotazioneNonnull() {
        CtReceiptModelResponse response =
                objectMapper.readValue(SENZA_CAMPI_OBBLIGATORI, CtReceiptModelResponse.class);

        assertThat(response.getCompanyName()).isNull();
        assertThat(response.getDebtor()).isNull();
        assertThat(response.getPaymentAmount()).isNull();
        assertThat(response.getIdPSP()).isNull();
        assertThat(response.getNoticeNumber()).isNull();
    }

    /**
     * Eccezione alla regola: il generatore inizializza le liste, quindi
     * {@code transferList} assente arriva vuota e non nulla. Per questo
     * {@code RicevutaJsonValidator} la verifica con "nulla <b>o</b> vuota":
     * controllare solo la nullita' lascerebbe passare una ricevuta senza
     * alcun trasferimento.
     */
    @Test
    void transferListAssenteArrivaVuotaNonNulla() {
        CtReceiptModelResponse response =
                objectMapper.readValue(SENZA_CAMPI_OBBLIGATORI, CtReceiptModelResponse.class);

        assertThat(response.getTransferList()).isNotNull().isEmpty();
    }

    /** Il validator deve comunque segnalarli tutti, qualunque sia la forma dell'assenza. */
    @Test
    void ilValidatorIntercettaQuellAssenza() {
        CtReceiptModelResponse response =
                objectMapper.readValue(SENZA_CAMPI_OBBLIGATORI, CtReceiptModelResponse.class);

        assertThatThrownBy(() -> new RicevutaJsonValidator().valida(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("companyName")
                .hasMessageContaining("debtor")
                .hasMessageContaining("paymentAmount")
                .hasMessageContaining("transferList");
    }
}
