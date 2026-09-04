package it.govpay.console.ricevuta.upload;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.util.StringUtils;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

import jakarta.xml.bind.Marshaller;

/**
 * Configurazione del client SOAP verso {@code api-pagopa} (paForNode),
 * porto da {@code GovpayClientConfig} in {@code govpay-rt-batch} verso lo
 * stesso endpoint. Nessun {@code SoapGdeCapturingInterceptor}: qui l'evento
 * GDE viene tracciato dal chiamante, non dal client.
 */
@Configuration
public class PaForNodeClientConfig {

    @Value("${app.pafornode.url}")
    private String url;

    @Value("${app.pafornode.auth.username}")
    private String username;

    @Value("${app.pafornode.auth.password}")
    private String password;

    @Value("${app.pafornode.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${app.pafornode.read-timeout-ms}")
    private int readTimeoutMs;

    @Bean
    public Jaxb2Marshaller paForNodeMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("it.gov.pagopa.pagopa_api.pa.pafornode");
        marshaller.setMarshallerProperties(Map.of(Marshaller.JAXB_FRAGMENT, Boolean.TRUE));
        return marshaller;
    }

    @Bean
    public PaForNodeRawClient paForNodeRawClient(Jaxb2Marshaller paForNodeMarshaller) {
        PaForNodeRawClient client = new PaForNodeRawClient();
        client.setDefaultUri(url);
        client.setMarshaller(paForNodeMarshaller);
        client.setUnmarshaller(paForNodeMarshaller);
        if (StringUtils.hasText(username)) {
            client.setInterceptors(new ClientInterceptor[] { new AuthorizationHeaderInserter(username, password) });
        }
        HttpUrlConnectionMessageSender messageSender = new HttpUrlConnectionMessageSender();
        messageSender.setConnectionTimeout(Duration.ofMillis(connectTimeoutMs));
        messageSender.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        client.setMessageSender(messageSender);
        return client;
    }
}
