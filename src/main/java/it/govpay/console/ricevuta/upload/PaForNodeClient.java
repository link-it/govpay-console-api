package it.govpay.console.ricevuta.upload;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.soap.client.SoapFaultClientException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtFaultBean;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtResponse;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTRes;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;
import it.gov.pagopa.pagopa_api.pa.pafornode.StOutcome;
import it.govpay.console.web.UnprocessableEntityException;

/**
 * Facade verso {@code api-pagopa} (paForNode) per l'invio di una RT caricata
 * da cruscotto. Responsabilita':
 * <ul>
 *   <li>cattura {@link CallNotPermittedException} (circuito aperto) e i
 *       fallimenti di trasporto ({@link WebServiceIOException}), rimappandoli
 *       su {@link PaForNodeTransportException} — non un esito definitivo,
 *       vedi il suo Javadoc;</li>
 *   <li>{@link SoapFaultClientException} (fault SOAP di protocollo) e' invece
 *       mappato direttamente su {@link UnprocessableEntityException} (→ 422),
 *       col dettaglio del fault: non e' un fallimento
 *       di trasporto da rileggere, e' un rifiuto della RT;</li>
 *   <li>interpreta anche l'esito applicativo in-band ({@link CtResponse#getOutcome()}):
 *       {@code KO} e' un rifiuto della RT da parte del Nodo, mappato sullo
 *       stesso 422 col dettaglio del fault — <b>tranne</b>
 *       {@code PAA_RECEIPT_DUPLICATA}, trattato come {@link PaForNodeTransportException}
 *       (vedi il suo Javadoc: puo' essere l'eco di un invio precedente
 *       riuscito la cui risposta e' andata persa).</li>
 * </ul>
 * Protetta da circuit breaker/retry (Resilience4j, instance {@code pafornode})
 * sul bean raw {@link PaForNodeRawClient} — vedi il Javadoc di
 * {@code it.govpay.console.avviso.StampeRawClient} per il ragionamento sulla
 * separazione dei bean.
 */
@Service
public class PaForNodeClient {

    private static final Logger log = LoggerFactory.getLogger(PaForNodeClient.class);

    private final PaForNodeRawClient rawClient;

    public PaForNodeClient(PaForNodeRawClient rawClient) {
        this.rawClient = rawClient;
    }

    /** Ramo JSON (§D): richiesta costruita dal converter, sempre {@code paSendRTV2}. */
    public PaSendRTV2Response inviaRicevutaV2(PaSendRTV2Request request) {
        PaSendRTV2Response response = call(() -> rawClient.inviaRicevutaV2(request));
        checkOutcome(response);
        return response;
    }

    /**
     * Ramo XML (§C/§E): {@code xml} e' gia' il corpo pronto per l'invio
     * (sbustato dal SOAP se necessario), inoltrato cosi' com'e' — nessun
     * unmarshal/remarshal. L'operazione SOAP e' scelta da
     * {@code formato}, gia' determinato da {@link RicevutaFormatDetector}
     * sull'elemento radice.
     */
    public CtResponse inviaRicevutaXml(byte[] xml, RicevutaFormato formato) {
        CtResponse response = switch (formato) {
            case V2_2 -> call(() -> rawClient.inviaRicevutaV2Raw(xml));
            case V2 -> call(() -> rawClient.inviaRicevutaRaw(xml));
            case JSON_PAGOPA -> throw new IllegalArgumentException(
                    "inviaRicevutaXml non supporta il formato JSON_PAGOPA: usare inviaRicevutaV2.");
        };
        checkOutcome(response);
        return response;
    }

    /**
     * {@code PAA_RECEIPT_DUPLICATA} non e' un rifiuto della RT: e' l'esito
     * normale quando un precedente tentativo e' effettivamente riuscito ma la
     * risposta e' andata persa (es. un retry di Resilience4j dopo un timeout
     * in lettura sull'invio precedente, gia' acquisito da core). Va trattato
     * come i fallimenti di trasporto — l'orchestratore rilegge la tupla prima
     * di decidere fra 201 e 502 — non come un 422 definitivo.
     */
    private static final String FAULT_CODE_RECEIPT_DUPLICATA = "PAA_RECEIPT_DUPLICATA";

    private static void checkOutcome(CtResponse response) {
        if (response == null || !StOutcome.KO.equals(response.getOutcome())) {
            return;
        }
        CtFaultBean fault = response.getFault();
        if (fault != null && FAULT_CODE_RECEIPT_DUPLICATA.equals(fault.getFaultCode())) {
            throw new PaForNodeTransportException(
                    "api-pagopa segnala RT gia' acquisita (PAA_RECEIPT_DUPLICATA): possibile esito di un "
                            + "tentativo precedente la cui risposta e' andata persa.", null, false);
        }
        throw new UnprocessableEntityException(faultDetail(fault));
    }

    private static String faultDetail(CtFaultBean fault) {
        return fault != null
                ? "RT rifiutata da api-pagopa: " + fault.getFaultCode() + " - " + fault.getFaultString()
                : "RT rifiutata da api-pagopa (nessun dettaglio di fault nella risposta).";
    }

    private <T> T call(Supplier<T> invocation) {
        try {
            return invocation.get();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker aperto sul client api-pagopa (paForNode): {}", e.getMessage());
            throw new PaForNodeTransportException(
                    "Microservizio api-pagopa momentaneamente non disponibile (circuit open).", e, false);
        } catch (SoapFaultClientException e) {
            log.warn("Fault SOAP da api-pagopa (paForNode): {}", e.getFaultStringOrReason());
            throw new UnprocessableEntityException(
                    "RT rifiutata da api-pagopa: " + e.getFaultStringOrReason(), e);
        } catch (WebServiceIOException e) {
            log.warn("Chiamata a api-pagopa (paForNode) fallita: {}", e.getMessage());
            throw new PaForNodeTransportException(
                    "Chiamata a api-pagopa (paForNode) fallita.", e, isTimeout(e));
        }
    }

    private static boolean isTimeout(WebServiceIOException e) {
        Throwable cause = e.getCause();
        return cause instanceof SocketTimeoutException
                || (cause instanceof IOException && cause.getMessage() != null
                        && cause.getMessage().toLowerCase().contains("timed out"));
    }
}
