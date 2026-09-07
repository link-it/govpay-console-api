package it.govpay.console.ricevuta.upload;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTReq;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.console.web.UnprocessableEntityException;

/**
 * Valida una RT XML caricata da cruscotto contro {@code paForNode.xsd} prima
 * dell'invio (issue #59 par. B/5: "un XML malformato o non conforme diventa
 * un 422 parlante lato console... la violazione della choice viene
 * intercettata prima dell'invio"). L'oggetto risultante dall'unmarshal viene
 * <b>scartato</b>: la validazione serve solo a intercettare violazioni dello
 * schema (in particolare la {@code <xsd:choice>} IBAN/MBDAttachment), il
 * corpo inoltrato a {@code api-pagopa} resta quello originale, byte per byte
 * (vedi {@link PaForNodeClient#inviaRicevutaXml}) — nessun round-trip
 * unmarshal/remarshal usato per costruire la richiesta.
 */
@Component
public class RicevutaXmlValidator {

    private static final String CONTEXT_PATH = "it.gov.pagopa.pagopa_api.pa.pafornode";
    private static final String XSD_CLASSPATH = "xsd/pagopa/paForNode.xsd";
    private static final String XSD_COMMON_CLASSPATH = "xsd/pagopa/sac-common-types-1.0.xsd";

    private final JAXBContext jaxbContext;
    private final Schema schema;

    public RicevutaXmlValidator() {
        try {
            this.jaxbContext = JAXBContext.newInstance(CONTEXT_PATH);
        } catch (JAXBException e) {
            throw new IllegalStateException("Impossibile inizializzare il contesto JAXB per " + CONTEXT_PATH, e);
        }
        this.schema = loadSchema();
    }

    /**
     * Carica lo schema passando <b>entrambi</b> i documenti come sorgenti dal
     * classpath: {@code paForNode.xsd} importa {@code sac-common-types-1.0.xsd} con
     * uno {@code schemaLocation} relativo, e precaricando il namespace importato
     * l'import viene soddisfatto dalla grammatica gia' in memoria invece che da una
     * risoluzione per URL.
     * <p>
     * E' quello che rende il caricamento <b>indipendente dal packaging</b>, ed e' la
     * ragione della correzione: passando un solo {@code StreamSource} sul systemId
     * della risorsa, il protocollo dell'URL deve essere fra quelli ammessi da
     * {@code accessExternalSchema}. Con il classpath esploso e' {@code file:} e
     * funziona; dentro il fat jar e' {@code jar:nested:} e l'avvio fallisce con
     * "'nested' access is not allowed". Precaricando, nessun accesso esterno serve
     * piu': {@code accessExternalSchema} resta a {@code ""}, cioe' piu' restrittivo
     * di prima e non meno.
     */
    private static Schema loadSchema() {
        try (InputStream common = risorsa(XSD_COMMON_CLASSPATH);
             InputStream paForNode = risorsa(XSD_CLASSPATH)) {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return schemaFactory.newSchema(new Source[] {
                    new StreamSource(common), new StreamSource(paForNode) });
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile caricare lo schema XSD " + XSD_CLASSPATH, e);
        }
    }

    private static InputStream risorsa(String percorso) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(percorso) : null;
        if (in == null) {
            in = RicevutaXmlValidator.class.getClassLoader().getResourceAsStream(percorso);
        }
        if (in == null) {
            throw new IllegalStateException("XSD non trovato sul classpath: " + percorso);
        }
        return in;
    }

    /** {@code formato} deve essere {@link RicevutaFormato#V2_2} o {@link RicevutaFormato#V2}. */
    public void validate(byte[] xml, RicevutaFormato formato) {
        Class<?> type = formato == RicevutaFormato.V2_2 ? PaSendRTV2Request.class : PaSendRTReq.class;
        unmarshal(xml, type);
    }

    private void unmarshal(byte[] xml, Class<?> type) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            unmarshaller.setSchema(schema);
            SAXParserFactory saxParserFactory = XmlHardening.newSecureSaxParserFactory();
            XMLReader xmlReader = saxParserFactory.newSAXParser().getXMLReader();
            Source source = new SAXSource(xmlReader, new InputSource(new ByteArrayInputStream(xml)));
            unmarshaller.unmarshal(source, type);
        } catch (Exception e) {
            // La causa (tipicamente un SAXParseException "cvc-...") porta il messaggio
            // parlante sulla violazione specifica; l'eccezione JAXB esterna
            // (UnmarshalException) spesso ha messaggio nullo.
            String dettaglio = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage()
                    : e.getMessage();
            throw new UnprocessableEntityException("RT non conforme allo schema XSD atteso: " + dettaglio, e);
        }
    }
}
