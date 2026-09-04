package it.govpay.console.ricevuta.upload;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.UnprocessableEntityException;

/**
 * Riconosce il formato di una RT caricata da cruscotto, dopo normalizzazione
 * ({@link RicevutaPayloadNormalizer}): il {@code Content-Type} dichiarato e'
 * solo un indizio, non la fonte di verita' — il contenuto puo' non
 * corrispondervi.
 *
 * <p>Estrae anche, dal contenuto stesso, la tupla
 * {@code (idDominio, iuv, idRicevuta)} — non e' business logic ma dispatch e
 * lettura di tre campi ("Riconoscere il formato e' dispatch, non business
 * logic"): serve al pre-flight duplicato e all'audit senza dover prima
 * normalizzare/convertire l'intero payload. Nessuna validazione semantica
 * della RT avviene qui.
 *
 * <p>Distinzione 400 vs 422: un payload che non e' ne'
 * JSON ne' XML ben formato e' una richiesta malformata (400); un XML ben
 * formato ma con radice non riconosciuta o non acquisibile (SANP 2.3.0
 * incluso), o un JSON ben formato ma non conforme al model atteso, e'
 * semanticamente un formato di RT non supportato (422).
 */
@Component
public class RicevutaFormatDetector {

    private static final String ROOT_V2_2 = "paSendRTV2Request";
    private static final String ROOT_V2 = "paSendRTReq";
    private static final String ROOT_SANP_230 = "RT";
    private static final String ELEMENT_RECEIPT = "receipt";

    private final ObjectMapper objectMapper;

    public RicevutaFormatDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RicevutaRiconosciuta detect(byte[] normalized) {
        int firstChar = firstSignificantByte(normalized);
        if (firstChar == '{') {
            return detectJson(normalized);
        }
        if (firstChar == '<') {
            return detectXml(normalized);
        }
        throw new BadRequestException("Payload non riconoscibile: atteso un documento XML o JSON.");
    }

    private RicevutaRiconosciuta detectJson(byte[] normalized) {
        JsonNode node;
        try {
            node = objectMapper.readTree(normalized);
        } catch (JacksonException e) {
            throw new BadRequestException("JSON non ben formato: " + e.getMessage());
        }
        if (node == null || !node.isObject()) {
            throw new BadRequestException("JSON non ben formato: atteso un oggetto JSON.");
        }
        return new RicevutaRiconosciuta(RicevutaFormato.JSON_PAGOPA,
                textOrNull(node, "fiscalCode"), textOrNull(node, "creditorReferenceId"),
                textOrNull(node, "receiptId"));
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asString() : null;
    }

    private RicevutaRiconosciuta detectXml(byte[] normalized) {
        Element root;
        try {
            DocumentBuilder builder = XmlHardening.newSecureDocumentBuilder();
            root = builder.parse(new ByteArrayInputStream(normalized)).getDocumentElement();
        } catch (Exception e) {
            throw new BadRequestException("XML non ben formato: " + e.getMessage());
        }
        String rootLocalName = root != null ? root.getLocalName() : null;

        RicevutaFormato formato;
        if (ROOT_V2_2.equals(rootLocalName)) {
            formato = RicevutaFormato.V2_2;
        } else if (ROOT_V2.equals(rootLocalName)) {
            formato = RicevutaFormato.V2;
        } else if (ROOT_SANP_230.equals(rootLocalName)) {
            throw new UnprocessableEntityException(
                    "Formato RT non supportato: SANP 2.3.0 (RT storica, elemento radice 'RT') non e' acquisibile da cruscotto.");
        } else {
            throw new UnprocessableEntityException(
                    "Formato RT non riconosciuto: elemento radice '" + rootLocalName + "' non atteso.");
        }

        Element receipt = firstChildElement(root, ELEMENT_RECEIPT);
        String idDominio = receipt != null ? childText(receipt, "fiscalCode") : null;
        String iuv = receipt != null ? childText(receipt, "creditorReferenceId") : null;
        String idRicevuta = receipt != null ? childText(receipt, "receiptId") : null;
        return new RicevutaRiconosciuta(formato, idDominio, iuv, idRicevuta);
    }

    private static Element firstChildElement(Element parent, String localName) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static String childText(Element parent, String localName) {
        Element child = firstChildElement(parent, localName);
        return child != null ? child.getTextContent() : null;
    }

    /** Salta whitespace e un eventuale BOM UTF-8, ritorna -1 se il payload e' vuoto. */
    private static int firstSignificantByte(byte[] data) {
        int i = 0;
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        for (; i < data.length; i++) {
            byte b = data[i];
            if (b != ' ' && b != '\t' && b != '\r' && b != '\n') {
                return b;
            }
        }
        return -1;
    }
}
