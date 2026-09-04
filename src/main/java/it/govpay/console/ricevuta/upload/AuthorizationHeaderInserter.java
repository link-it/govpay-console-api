package it.govpay.console.ricevuta.upload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;

/**
 * Autenticazione BASIC sulla chiamata SOAP verso {@code api-pagopa}. Porto da
 * {@code govpay-rt-batch} (client analogo verso lo stesso endpoint paForNode),
 * nessuna logica specifica di quel progetto qui dentro.
 */
public class AuthorizationHeaderInserter implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationHeaderInserter.class);

    private final String authorizationHeaderValue;

    public AuthorizationHeaderInserter(String username, String password) {
        String credentials = username + ":" + password;
        this.authorizationHeaderValue = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
        log.debug("Adding HTTP Basic authentication header to the soap request");
        TransportContext context = TransportContextHolder.getTransportContext();
        WebServiceConnection connection = context.getConnection();
        if (connection instanceof HeadersAwareSenderWebServiceConnection httpConnection) {
            try {
                httpConnection.addRequestHeader("Authorization", authorizationHeaderValue);
            } catch (IOException e) {
                throw new WebServiceIOException("Fail to insert authorization header", e);
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
