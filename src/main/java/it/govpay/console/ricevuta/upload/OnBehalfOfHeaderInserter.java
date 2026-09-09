package it.govpay.console.ricevuta.upload;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;

/**
 * Comunica ad {@code api-pagopa} il principal dell'operatore che ha caricato
 * la RT da cruscotto, tramite l'header {@code GOVPAY-ON-BEHALF-OF}. A
 * differenza di {@link AuthorizationHeaderInserter} (credenziali fisse), il
 * valore va letto per-richiesta: la chiamata SOAP e' sincrona sullo stesso
 * thread della richiesta HTTP, quindi il {@link SecurityContextHolder}
 * (strategia di default {@code ThreadLocal}) e' ancora valido a questo punto.
 */
public class OnBehalfOfHeaderInserter implements ClientInterceptor {

    private static final String HEADER_NAME = "GOVPAY-ON-BEHALF-OF";

    private static final Logger log = LoggerFactory.getLogger(OnBehalfOfHeaderInserter.class);

    @Override
    public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Nessun principal autenticato nel SecurityContext: header {} non inserito.", HEADER_NAME);
            return true;
        }
        TransportContext context = TransportContextHolder.getTransportContext();
        WebServiceConnection connection = context.getConnection();
        if (connection instanceof HeadersAwareSenderWebServiceConnection httpConnection) {
            try {
                httpConnection.addRequestHeader(HEADER_NAME, authentication.getName());
            } catch (IOException e) {
                throw new WebServiceIOException("Fail to insert " + HEADER_NAME + " header", e);
            }
        }
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
        return true;
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
        // Nothing to do
    }
}
