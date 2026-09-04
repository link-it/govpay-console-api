package it.govpay.console.ricevuta.upload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Porto da {@code RtUtils.decodeOrOriginal}/{@code RtUtils.extractSoapMessage}
 * (V1, {@code it.govpay.core.utils.RtUtils}): stessa logica — un payload di
 * RT caricato da cruscotto puo' arrivare in chiaro, in base64, dentro una
 * busta SOAP, o entrambe le cose insieme.
 *
 * <p>Due divergenze deliberate rispetto a V1:
 * <ul>
 *   <li>il parsing della busta SOAP usa un {@link DocumentBuilder}
 *       hardenizzato contro XXE ({@link XmlHardening}) — V1 non hardenizza
 *       ne' questo passaggio ne' l'unmarshalling JAXB successivo, perche' li'
 *       l'XML proviene dal Nodo, non da un caricamento;</li>
 *   <li>l'estrazione del contenuto del Body usa {@link StandardCharsets#UTF_8}
 *       esplicito, non il charset di default della piattaforma.</li>
 * </ul>
 */
@Component
public class RicevutaPayloadNormalizer {

    private static final Logger log = LoggerFactory.getLogger(RicevutaPayloadNormalizer.class);

    private static final Set<String> SOAP_ENVELOPE_NAMESPACES = Set.of(
            "http://schemas.xmlsoap.org/soap/envelope/",
            "http://www.w3.org/2003/05/soap-envelope");

    public byte[] normalize(byte[] raw) {
        byte[] decoded = decodeOrOriginal(raw);
        return extractSoapMessage(decoded);
    }

    private byte[] decodeOrOriginal(byte[] data) {
        try {
            byte[] decoded = Base64.getDecoder().decode(data);
            log.trace("Il messaggio contenente un base64 e' stato decodificato");
            return decoded;
        } catch (IllegalArgumentException e) {
            log.trace("Il messaggio non contiene un base64");
            return data;
        }
    }

    private byte[] extractSoapMessage(byte[] data) {
        try {
            DocumentBuilder builder = XmlHardening.newSecureDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(data));
            Element root = document.getDocumentElement();
            if (root == null || !"Envelope".equals(root.getLocalName())
                    || !SOAP_ENVELOPE_NAMESPACES.contains(root.getNamespaceURI())) {
                log.trace("Il messaggio non contiene una busta SOAP");
                return data;
            }
            Element body = firstChildElement(root, "Body");
            if (body == null) {
                log.trace("Busta SOAP senza Body, il messaggio viene trattato come non imbustato");
                return data;
            }
            Element content = firstChildElementAny(body);
            if (content == null) {
                log.trace("Body SOAP vuoto, il messaggio viene trattato come non imbustato");
                return data;
            }
            log.trace("Il messaggio contiene una busta SOAP");
            return serialize(content);
        } catch (Exception e) {
            log.trace("Il messaggio non contiene una busta SOAP valida", e);
            return data;
        }
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

    private static Element firstChildElementAny(Element parent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static byte[] serialize(Element element) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(element), new StreamResult(out));
        return out.toByteArray();
    }
}
