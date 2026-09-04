package it.govpay.console.ricevuta.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import it.gov.pagopa.pagopa_api.pa.pafornode.CtFaultBean;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.pa.pafornode.StOutcome;
import it.govpay.console.web.UnprocessableEntityException;

class PaForNodeClientTest {

    private final PaForNodeRawClient rawClient = mock(PaForNodeRawClient.class);
    private final PaForNodeClient client = new PaForNodeClient(rawClient);

    @Test
    void outcomeOkRitornaLaRisposta() {
        PaSendRTV2Response ok = new PaSendRTV2Response();
        ok.setOutcome(StOutcome.OK);
        when(rawClient.inviaRicevutaV2(any())).thenReturn(ok);

        assertThat(client.inviaRicevutaV2(new PaSendRTV2Request())).isSameAs(ok);
    }

    @Test
    void faultGenericoRitornaUnprocessableEntity() {
        PaSendRTV2Response ko = new PaSendRTV2Response();
        ko.setOutcome(StOutcome.KO);
        CtFaultBean fault = new CtFaultBean();
        fault.setFaultCode("PPT_SEMANTICA");
        fault.setFaultString("RT non valida");
        ko.setFault(fault);
        when(rawClient.inviaRicevutaV2(any())).thenReturn(ko);

        assertThatThrownBy(() -> client.inviaRicevutaV2(new PaSendRTV2Request()))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("PPT_SEMANTICA");
    }

    /**
     * PAA_RECEIPT_DUPLICATA puo' essere l'eco di un invio precedente riuscito
     * la cui risposta e' andata persa (es. un retry dopo un timeout in
     * lettura): non deve diventare un 422 definitivo, ma un
     * {@link PaForNodeTransportException} che l'orchestratore intercetta per
     * rileggere la tupla prima di decidere fra 201 e 502.
     */
    @Test
    void receiptDuplicataNonRitornaUnprocessableEntityMaTransportException() {
        PaSendRTV2Response ko = new PaSendRTV2Response();
        ko.setOutcome(StOutcome.KO);
        CtFaultBean fault = new CtFaultBean();
        fault.setFaultCode("PAA_RECEIPT_DUPLICATA");
        fault.setFaultString("RT gia' acquisita");
        ko.setFault(fault);
        when(rawClient.inviaRicevutaV2(any())).thenReturn(ko);

        assertThatThrownBy(() -> client.inviaRicevutaV2(new PaSendRTV2Request()))
                .isInstanceOf(PaForNodeTransportException.class)
                .satisfies(e -> assertThat(((PaForNodeTransportException) e).isTimeout()).isFalse());
    }
}
