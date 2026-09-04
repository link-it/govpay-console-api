package it.govpay.console.ricevuta.upload;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Fabbriche di parser XML hardenizzate contro XXE (entita' esterne, accesso
 * a DTD esterne), secondo le raccomandazioni OWASP. Usate ovunque questo
 * package elabori XML fornito dall'operatore (issue #59 par. C): a
 * differenza della lettura da DB in {@code JaxbUtils} (fidata, mai
 * hardenizzata li'), qui l'input non e' fidato.
 */
final class XmlHardening {

    private XmlHardening() {
    }

    static DocumentBuilderFactory newSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Impossibile hardenizzare il DocumentBuilderFactory contro XXE.", e);
        }
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        return factory;
    }

    /**
     * Come {@link #newSecureDocumentBuilderFactory()}, con un
     * {@link ErrorHandler} silenzioso: gli errori di parsing (XML malformato,
     * DOCTYPE rifiutato) sono attesi e gestiti dal chiamante — senza questo
     * handler Xerces li stampa comunque su stderr.
     */
    static DocumentBuilder newSecureDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilder builder = newSecureDocumentBuilderFactory().newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                // ignorato: il chiamante decide se il parsing e' comunque fallito
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return builder;
    }

    static SAXParserFactory newSecureSaxParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Impossibile hardenizzare il SAXParserFactory contro XXE.", e);
        }
        factory.setXIncludeAware(false);
        factory.setNamespaceAware(true);
        return factory;
    }
}
