package it.govpay.console.ricevuta.upload;

import java.io.ByteArrayInputStream;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.SourceExtractor;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.client.core.SoapActionCallback;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTRes;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Response;

/**
 * Chiamata SOAP grezza verso {@code api-pagopa} (paForNode), isolata in un
 * bean a sé perché {@code @CircuitBreaker}/{@code @Retry} sono AOP — vedi
 * Javadoc di {@code it.govpay.console.avviso.StampeRawClient} per il
 * ragionamento completo (self-invocation bypassa il proxy).
 *
 * <p>Due percorsi di invio distinti:
 * <ul>
 *   <li>{@link #inviaRicevutaV2(PaSendRTV2Request)} — richiesta tipizzata,
 *       marshallata da {@code Jaxb2Marshaller}: usato dal ramo JSON (§D), che
 *       costruisce l'oggetto col converter e produce sempre {@code paSendRTV2};</li>
 *   <li>{@link #inviaRicevutaV2Raw(byte[])}/{@link #inviaRicevutaRaw(byte[])}
 *       — corpo XML gia' pronto (sbustato dal SOAP, se necessario) inoltrato
 *       cosi' com'e', <b>senza</b> unmarshal/remarshal: usato dal ramo XML,
 *       che preserva il documento caricato byte per byte (il body stesso non
 *       viene ricostruito, non che manchi ogni parsing: la sola response
 *       resta tipizzata tramite l'{@code Unmarshaller} configurato). Prendono
 *       {@code byte[]} e non un {@link Source} gia' costruito apposta: un
 *       {@code Source} basato su uno stream e' consumato al primo tentativo,
 *       e {@code @Retry} rieseguirebbe l'invio con uno stream gia' esaurito —
 *       il {@link StreamSource} va quindi ricreato a ogni tentativo, dentro
 *       il metodo annotato.</li>
 * </ul>
 */
@Service
public class PaForNodeRawClient extends WebServiceGatewaySupport {

    @CircuitBreaker(name = "pafornode")
    @Retry(name = "pafornode")
    public PaSendRTV2Response inviaRicevutaV2(PaSendRTV2Request request) {
        WebServiceTemplate template = getWebServiceTemplate();
        Object result = template.marshalSendAndReceive(request, new SoapActionCallback("paSendRTV2"));
        return (PaSendRTV2Response) result;
    }

    @CircuitBreaker(name = "pafornode")
    @Retry(name = "pafornode")
    public PaSendRTV2Response inviaRicevutaV2Raw(byte[] requestBody) {
        return sendRaw(requestBody, "paSendRTV2", PaSendRTV2Response.class);
    }

    @CircuitBreaker(name = "pafornode")
    @Retry(name = "pafornode")
    public PaSendRTRes inviaRicevutaRaw(byte[] requestBody) {
        return sendRaw(requestBody, "paSendRT", PaSendRTRes.class);
    }

    private <T> T sendRaw(byte[] requestBody, String soapAction, Class<T> responseType) {
        WebServiceTemplate template = getWebServiceTemplate();
        Source source = new StreamSource(new ByteArrayInputStream(requestBody));
        SourceExtractor<Object> extractor = src -> getUnmarshaller().unmarshal(src);
        Object result = template.sendSourceAndReceive(source, new SoapActionCallback(soapAction), extractor);
        return responseType.cast(result);
    }
}
