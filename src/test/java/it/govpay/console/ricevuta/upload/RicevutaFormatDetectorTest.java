package it.govpay.console.ricevuta.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.UnprocessableEntityException;

class RicevutaFormatDetectorTest {

    private final RicevutaFormatDetector detector = new RicevutaFormatDetector(new ObjectMapper());

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static final String XML_V2_2 = "<paSendRTV2Request><receipt>"
            + "<fiscalCode>77777777777</fiscalCode>"
            + "<creditorReferenceId>iuv123</creditorReferenceId>"
            + "<receiptId>iur456</receiptId>"
            + "</receipt></paSendRTV2Request>";

    private static final String XML_V2 = "<paSendRTReq><receipt>"
            + "<fiscalCode>77777777777</fiscalCode>"
            + "<creditorReferenceId>iuv123</creditorReferenceId>"
            + "<receiptId>iur456</receiptId>"
            + "</receipt></paSendRTReq>";

    @Test
    void riconoscePaSendRTV2RequestComeV2_2EdEstraeLaTupla() {
        RicevutaRiconosciuta esito = detector.detect(bytes(XML_V2_2));
        assertThat(esito.formato()).isEqualTo(RicevutaFormato.V2_2);
        assertThat(esito.idDominio()).isEqualTo("77777777777");
        assertThat(esito.iuv()).isEqualTo("iuv123");
        assertThat(esito.idRicevuta()).isEqualTo("iur456");
    }

    @Test
    void riconoscePaSendRTReqComeV2EdEstraeLaTupla() {
        RicevutaRiconosciuta esito = detector.detect(bytes(XML_V2));
        assertThat(esito.formato()).isEqualTo(RicevutaFormato.V2);
        assertThat(esito.idDominio()).isEqualTo("77777777777");
        assertThat(esito.iuv()).isEqualTo("iuv123");
        assertThat(esito.idRicevuta()).isEqualTo("iur456");
    }

    @Test
    void xmlSenzaReceiptRitornaTuplaNulla() {
        RicevutaRiconosciuta esito = detector.detect(bytes("<paSendRTV2Request/>"));
        assertThat(esito.formato()).isEqualTo(RicevutaFormato.V2_2);
        assertThat(esito.idDominio()).isNull();
        assertThat(esito.iuv()).isNull();
        assertThat(esito.idRicevuta()).isNull();
    }

    @Test
    void riconosceJsonDalPrimoCarattereSignificativoEdEstraeLaTupla() {
        String json = "  \n  {\"fiscalCode\":\"77777777777\",\"creditorReferenceId\":\"iuv123\","
                + "\"receiptId\":\"iur456\",\"outcome\":\"OK\"}";
        RicevutaRiconosciuta esito = detector.detect(bytes(json));
        assertThat(esito.formato()).isEqualTo(RicevutaFormato.JSON_PAGOPA);
        assertThat(esito.idDominio()).isEqualTo("77777777777");
        assertThat(esito.iuv()).isEqualTo("iuv123");
        assertThat(esito.idRicevuta()).isEqualTo("iur456");
    }

    @Test
    void jsonConCampiMancantiRitornaTuplaParziale() {
        RicevutaRiconosciuta esito = detector.detect(bytes("{\"outcome\":\"OK\"}"));
        assertThat(esito.formato()).isEqualTo(RicevutaFormato.JSON_PAGOPA);
        assertThat(esito.idDominio()).isNull();
    }

    @Test
    void jsonNonOggettoRitornaBadRequest() {
        assertThatThrownBy(() -> detector.detect(bytes("[1,2,3]")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void jsonMalformatoRitornaBadRequest() {
        assertThatThrownBy(() -> detector.detect(bytes("{\"outcome\": ")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void ignoraBomUtf8PrimaDiRiconoscereXml() {
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] xml = bytes(XML_V2);
        byte[] withBom = new byte[bom.length + xml.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(xml, 0, withBom, bom.length, xml.length);
        assertThat(detector.detect(withBom).formato()).isEqualTo(RicevutaFormato.V2);
    }

    @Test
    void radiceSanp230RitornaUnprocessableEntity() {
        assertThatThrownBy(() -> detector.detect(bytes("<RT><foo/></RT>")))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("SANP 2.3.0");
    }

    @Test
    void radiceNonRiconosciutaRitornaUnprocessableEntity() {
        assertThatThrownBy(() -> detector.detect(bytes("<qualcosaDiInatteso/>")))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("non riconosciuto");
    }

    @Test
    void xmlMalformatoRitornaBadRequest() {
        assertThatThrownBy(() -> detector.detect(bytes("<paSendRTReq><foo/>")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("non ben formato");
    }

    @Test
    void payloadNeXmlNeJsonRitornaBadRequest() {
        assertThatThrownBy(() -> detector.detect(bytes("qualcosa a caso")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void payloadVuotoRitornaBadRequest() {
        assertThatThrownBy(() -> detector.detect(new byte[0]))
                .isInstanceOf(BadRequestException.class);
    }
}
