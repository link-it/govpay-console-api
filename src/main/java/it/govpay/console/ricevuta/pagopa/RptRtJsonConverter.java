package it.govpay.console.ricevuta.pagopa;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;

import it.govpay.console.entity.Rpt;
import jakarta.xml.bind.JAXBException;

/**
 * Converte i tracciati XML PagoPA (RPT e RT, conservati come XML nel DB) nel
 * loro equivalente JSON. Il modello effettivamente serializzato dipende dalla
 * versione SANP della transazione: per ogni generazione si effettua
 * l'unmarshalling sul tipo JAXB corretto e si serializza il sotto-albero
 * significativo (la richiesta/ricevuta vera e propria).
 */
@Component
public class RptRtJsonConverter {

	private final ObjectMapper mapper;

	public RptRtJsonConverter() {
		this.mapper = new ObjectMapper();
		this.mapper.registerModule(new JakartaXmlBindAnnotationModule());
		this.mapper.registerModule(new PagoPaDateModule());
		this.mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
		this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	/**
	 * @return il JSON della RPT, oppure {@code null} se l'XML della richiesta non
	 *         è disponibile (transazioni acquisite in standin: la richiesta non
	 *         viene ricostruita).
	 */
	public JsonNode toRptJson(Rpt rpt) {
		byte[] xml = rpt.getXmlRpt();
		if (xml == null) {
			return null;
		}
		try {
			Object messaggio = switch (versione(rpt)) {
				case "SANP_230", "RPTSANP230_RTV2" -> JaxbUtils.toRPT(xml);
				case "SANP_240", "RPTV1_RTV2" -> JaxbUtils.toPaGetPaymentResRPT(xml).getData();
				case "SANP_321_V2", "RPTV2_RTV1" -> JaxbUtils.toPaGetPaymentV2ResponseRPT(xml).getData();
				default -> throw versioneNonGestita(rpt);
			};
			return mapper.valueToTree(messaggio);
		} catch (JAXBException e) {
			throw new RptRtConversionException("Errore nella conversione della RPT (versione " + versione(rpt) + ")", e);
		}
	}

	/**
	 * @return il JSON della RT, oppure {@code null} se l'XML della ricevuta non è
	 *         ancora presente.
	 */
	public JsonNode toRtJson(Rpt rpt) {
		byte[] xml = rpt.getXmlRt();
		if (xml == null) {
			return null;
		}
		try {
			Object messaggio = switch (versione(rpt)) {
				case "SANP_230" -> JaxbUtils.toRT(xml);
				case "SANP_240", "RPTV2_RTV1" -> JaxbUtils.toPaSendRTReqRT(xml).getReceipt();
				case "SANP_321_V2", "RPTV1_RTV2", "RPTSANP230_RTV2" -> JaxbUtils.toPaSendRTV2RequestRT(xml).getReceipt();
				default -> throw versioneNonGestita(rpt);
			};
			return mapper.valueToTree(messaggio);
		} catch (JAXBException e) {
			throw new RptRtConversionException("Errore nella conversione della RT (versione " + versione(rpt) + ")", e);
		}
	}

	private static String versione(Rpt rpt) {
		return rpt.getVersione();
	}

	private static RptRtConversionException versioneNonGestita(Rpt rpt) {
		return new RptRtConversionException("Versione SANP non gestita per la conversione del tracciato: " + versione(rpt));
	}
}
