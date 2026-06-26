package it.govpay.console.ricevuta.pagopa;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
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

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

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
		Object messaggio = rptMessaggio(rpt);
		return messaggio == null ? null : mapper.valueToTree(messaggio);
	}

	/**
	 * @return il JSON della RT, oppure {@code null} se l'XML della ricevuta non è
	 *         ancora presente.
	 */
	public JsonNode toRtJson(Rpt rpt) {
		Object messaggio = rtMessaggio(rpt);
		return messaggio == null ? null : mapper.valueToTree(messaggio);
	}

	/** Variante {@code Map} della RPT, per l'embedding inline nel modello {@code Ricevuta}. */
	public Map<String, Object> toRptMap(Rpt rpt) {
		Object messaggio = rptMessaggio(rpt);
		return messaggio == null ? null : mapper.convertValue(messaggio, MAP_TYPE);
	}

	/** Variante {@code Map} della RT, per l'embedding inline nel modello {@code Ricevuta}. */
	public Map<String, Object> toRtMap(Rpt rpt) {
		Object messaggio = rtMessaggio(rpt);
		return messaggio == null ? null : mapper.convertValue(messaggio, MAP_TYPE);
	}

	/** Unmarshalla e seleziona il sotto-albero RPT da serializzare, o {@code null} se l'XML manca. */
	private Object rptMessaggio(Rpt rpt) {
		byte[] xml = rpt.getXmlRpt();
		if (xml == null) {
			return null;
		}
		try {
			return switch (versione(rpt)) {
				case "SANP_230", "RPTSANP230_RTV2" -> JaxbUtils.toRPT(xml);
				case "SANP_240", "RPTV1_RTV2" -> JaxbUtils.toPaGetPaymentResRPT(xml).getData();
				case "SANP_321_V2", "RPTV2_RTV1" -> JaxbUtils.toPaGetPaymentV2ResponseRPT(xml).getData();
				default -> throw versioneNonGestita(rpt);
			};
		} catch (JAXBException e) {
			throw new RptRtConversionException("Errore nella conversione della RPT (versione " + versione(rpt) + ")", e);
		}
	}

	/** Unmarshalla e seleziona il sotto-albero RT da serializzare, o {@code null} se l'XML manca. */
	private Object rtMessaggio(Rpt rpt) {
		byte[] xml = rpt.getXmlRt();
		if (xml == null) {
			return null;
		}
		try {
			return switch (versione(rpt)) {
				case "SANP_230" -> JaxbUtils.toRT(xml);
				case "SANP_240", "RPTV2_RTV1" -> JaxbUtils.toPaSendRTReqRT(xml).getReceipt();
				case "SANP_321_V2", "RPTV1_RTV2", "RPTSANP230_RTV2" -> JaxbUtils.toPaSendRTV2RequestRT(xml).getReceipt();
				default -> throw versioneNonGestita(rpt);
			};
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
