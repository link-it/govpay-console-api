package it.govpay.console.ricevuta.pagopa;

import java.io.ByteArrayInputStream;

import javax.xml.transform.stream.StreamSource;

import it.gov.digitpa.schemas._2011.pagamenti.RPT;
import it.gov.digitpa.schemas._2011.pagamenti.RT;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaGetPaymentRes;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaGetPaymentV2Response;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTReq;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

/**
 * Unmarshalling JAXB dei tracciati XML PagoPA (RPT/RT) verso i modelli generati.
 * Solo lettura: l'XML proviene dal DB e non viene validato contro gli XSD
 * (la validazione è già garantita a monte dal Nodo).
 */
public final class JaxbUtils {

	/** Tracciati digitpa SANP 2.3.0 (RPT/RT "storici"). */
	private static final JAXBContext RPT_RT_CONTEXT = newContext("it.gov.digitpa.schemas._2011.pagamenti");
	/** Tracciati paForNode (SANP 2.4.0 e 3.2.1 V2). */
	private static final JAXBContext PA_FOR_NODE_CONTEXT = newContext("it.gov.pagopa.pagopa_api.pa.pafornode");

	private JaxbUtils() {}

	private static JAXBContext newContext(String packageName) {
		try {
			return JAXBContext.newInstance(packageName);
		} catch (JAXBException e) {
			throw new IllegalStateException("Impossibile inizializzare il contesto JAXB per il package " + packageName, e);
		}
	}

	private static <T> T unmarshal(JAXBContext context, byte[] xml, Class<T> type) throws JAXBException {
		Unmarshaller unmarshaller = context.createUnmarshaller();
		return unmarshaller.unmarshal(new StreamSource(new ByteArrayInputStream(xml)), type).getValue();
	}

	public static RPT toRPT(byte[] xml) throws JAXBException {
		return unmarshal(RPT_RT_CONTEXT, xml, RPT.class);
	}

	public static RT toRT(byte[] xml) throws JAXBException {
		return unmarshal(RPT_RT_CONTEXT, xml, RT.class);
	}

	public static PaGetPaymentRes toPaGetPaymentResRPT(byte[] xml) throws JAXBException {
		return unmarshal(PA_FOR_NODE_CONTEXT, xml, PaGetPaymentRes.class);
	}

	public static PaSendRTReq toPaSendRTReqRT(byte[] xml) throws JAXBException {
		return unmarshal(PA_FOR_NODE_CONTEXT, xml, PaSendRTReq.class);
	}

	public static PaGetPaymentV2Response toPaGetPaymentV2ResponseRPT(byte[] xml) throws JAXBException {
		return unmarshal(PA_FOR_NODE_CONTEXT, xml, PaGetPaymentV2Response.class);
	}

	public static PaSendRTV2Request toPaSendRTV2RequestRT(byte[] xml) throws JAXBException {
		return unmarshal(PA_FOR_NODE_CONTEXT, xml, PaSendRTV2Request.class);
	}
}
