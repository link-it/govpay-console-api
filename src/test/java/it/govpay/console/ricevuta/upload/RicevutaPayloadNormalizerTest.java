package it.govpay.console.ricevuta.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class RicevutaPayloadNormalizerTest {

    private final RicevutaPayloadNormalizer normalizer = new RicevutaPayloadNormalizer();

    @Test
    void xmlInChiaroNonVieneAlterato() {
        byte[] xml = "<paSendRTReq><foo>bar</foo></paSendRTReq>".getBytes(StandardCharsets.UTF_8);
        assertThat(normalizer.normalize(xml)).isEqualTo(xml);
    }

    @Test
    void base64VieneDecodificato() {
        byte[] xml = "<paSendRTReq><foo>bar</foo></paSendRTReq>".getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64.getEncoder().encode(xml);
        assertThat(normalizer.normalize(encoded)).isEqualTo(xml);
    }

    @Test
    void bustaSoap11VieneSbustata() {
        String envelope = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Body><paSendRTReq><foo>bar</foo></paSendRTReq></soapenv:Body>"
                + "</soapenv:Envelope>";
        byte[] normalized = normalizer.normalize(envelope.getBytes(StandardCharsets.UTF_8));
        String result = new String(normalized, StandardCharsets.UTF_8);
        assertThat(result).contains("<paSendRTReq>").doesNotContain("Envelope");
    }

    @Test
    void bustaSoap12VieneSbustata() {
        String envelope = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap:Body><paSendRTV2Request><foo>bar</foo></paSendRTV2Request></soap:Body>"
                + "</soap:Envelope>";
        byte[] normalized = normalizer.normalize(envelope.getBytes(StandardCharsets.UTF_8));
        assertThat(new String(normalized, StandardCharsets.UTF_8)).contains("<paSendRTV2Request>");
    }

    @Test
    void base64ESoapCombinatiVengonoGestitiInsieme() {
        String envelope = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Body><paSendRTReq><foo>bar</foo></paSendRTReq></soapenv:Body>"
                + "</soapenv:Envelope>";
        byte[] encoded = Base64.getEncoder().encode(envelope.getBytes(StandardCharsets.UTF_8));
        byte[] normalized = normalizer.normalize(encoded);
        assertThat(new String(normalized, StandardCharsets.UTF_8)).contains("<paSendRTReq>");
    }

    @Test
    void xmlNonEnvelopeNonVieneToccato() {
        byte[] xml = "<paSendRTV2Request><foo>bar</foo></paSendRTV2Request>".getBytes(StandardCharsets.UTF_8);
        assertThat(normalizer.normalize(xml)).isEqualTo(xml);
    }

    @Test
    void xmlMalformatoRitornaOriginaleSenzaEccezione() {
        byte[] malformato = "<paSendRTReq><foo>bar</foo>".getBytes(StandardCharsets.UTF_8);
        assertThat(normalizer.normalize(malformato)).isEqualTo(malformato);
    }

    @Test
    void jsonNonVieneToccato() {
        byte[] json = "{\"outcome\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(normalizer.normalize(json)).isEqualTo(json);
    }

    @Test
    void doctypeNellaBustaSoapVieneRifiutatoENonEspanso() {
        String envelope = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE soapenv:Envelope [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Body><paSendRTReq>&xxe;</paSendRTReq></soapenv:Body>"
                + "</soapenv:Envelope>";
        byte[] raw = envelope.getBytes(StandardCharsets.UTF_8);
        // Il parser hardenizzato rifiuta il DOCTYPE: il messaggio non viene sbustato,
        // l'entita' esterna non viene mai risolta.
        assertThat(normalizer.normalize(raw)).isEqualTo(raw);
    }
}
