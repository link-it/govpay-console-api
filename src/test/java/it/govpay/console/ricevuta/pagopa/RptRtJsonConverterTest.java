package it.govpay.console.ricevuta.pagopa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.gov.digitpa.schemas._2011.pagamenti.RPT;
import it.gov.digitpa.schemas._2011.pagamenti.RT;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtPaymentPA;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtPaymentPAV2;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtReceipt;
import it.gov.pagopa.pagopa_api.pa.pafornode.CtReceiptV2;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaGetPaymentRes;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaGetPaymentV2Response;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTReq;
import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.gov.pagopa.pagopa_api.pa.pafornode.StOutcome;
import it.govpay.console.entity.Rpt;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

/**
 * Smoke test della conversione XML→JSON sui tre tracciati SANP (230 / 240 / 321 V2),
 * lato RPT e lato RT. L'XML è prodotto marshallando i modelli generati (gli stessi
 * usati dal Nodo) così da esercitare l'intera pipeline unmarshalling → dispatch su
 * versione → serializzazione Jackson, incluse le date passate dagli adapter.
 */
class RptRtJsonConverterTest {

	private static final String DIGITPA_PKG = "it.gov.digitpa.schemas._2011.pagamenti";
	private static final String PA_FOR_NODE_PKG = "it.gov.pagopa.pagopa_api.pa.pafornode";

	private final RptRtJsonConverter converter = new RptRtJsonConverter();

	private static byte[] marshal(Object jaxbRoot, String packageName) throws JAXBException {
		Marshaller marshaller = JAXBContext.newInstance(packageName).createMarshaller();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		marshaller.marshal(jaxbRoot, baos);
		return baos.toByteArray();
	}

	private static Rpt rptWith(String versione, byte[] xmlRpt, byte[] xmlRt) {
		Rpt rpt = new Rpt();
		rpt.setVersione(versione);
		rpt.setXmlRpt(xmlRpt);
		rpt.setXmlRt(xmlRt);
		return rpt;
	}

	// ---- RPT ----------------------------------------------------------------

	@Test
	void rptSanp230IsConvertedFromRootRpt() throws JAXBException {
		RPT model = new RPT();
		model.setVersioneOggetto("6.2");
		model.setIdentificativoMessaggioRichiesta("MSG-230");
		model.setDataOraMessaggioRichiesta(LocalDateTime.of(2026, 6, 24, 10, 15, 30));
		Rpt rpt = rptWith("SANP_230", marshal(model, DIGITPA_PKG), null);

		JsonNode json = converter.toRptJson(rpt);

		assertThat(json.get("identificativoMessaggioRichiesta").asText()).isEqualTo("MSG-230");
		assertThat(json.get("dataOraMessaggioRichiesta").asText()).isEqualTo("2026-06-24T10:15:30");
	}

	@Test
	void rptSanp240IsConvertedFromPaymentData() throws JAXBException {
		CtPaymentPA data = new CtPaymentPA();
		data.setCreditorReferenceId("IUV-240");
		data.setPaymentAmount(new BigDecimal("123.45"));
		data.setDueDate(LocalDate.of(2026, 7, 1));
		data.setRetentionDate(LocalDateTime.of(2026, 6, 30, 23, 59, 59));
		PaGetPaymentRes res = new PaGetPaymentRes();
		res.setData(data);
		Rpt rpt = rptWith("SANP_240", marshal(res, PA_FOR_NODE_PKG), null);

		JsonNode json = converter.toRptJson(rpt);

		// il converter serializza il sotto-albero data, non il wrapper
		assertThat(json.has("data")).isFalse();
		assertThat(json.get("creditorReferenceId").asText()).isEqualTo("IUV-240");
		assertThat(json.get("paymentAmount").asText()).isEqualTo("123.45");
		assertThat(json.get("dueDate").asText()).isEqualTo("2026-07-01");
	}

	@Test
	void rptSanp321IsConvertedFromPaymentV2Data() throws JAXBException {
		CtPaymentPAV2 data = new CtPaymentPAV2();
		data.setCreditorReferenceId("IUV-321");
		data.setDueDate(LocalDate.of(2026, 8, 15));
		PaGetPaymentV2Response res = new PaGetPaymentV2Response();
		res.setData(data);
		Rpt rpt = rptWith("SANP_321_V2", marshal(res, PA_FOR_NODE_PKG), null);

		JsonNode json = converter.toRptJson(rpt);

		assertThat(json.has("data")).isFalse();
		assertThat(json.get("creditorReferenceId").asText()).isEqualTo("IUV-321");
		assertThat(json.get("dueDate").asText()).isEqualTo("2026-08-15");
	}

	// ---- RT -----------------------------------------------------------------

	@Test
	void rtSanp230IsConvertedFromRootRt() throws JAXBException {
		RT model = new RT();
		model.setIdentificativoMessaggioRicevuta("RT-230");
		model.setDataOraMessaggioRicevuta(LocalDateTime.of(2026, 6, 24, 11, 0, 0));
		Rpt rpt = rptWith("SANP_230", null, marshal(model, DIGITPA_PKG));

		JsonNode json = converter.toRtJson(rpt);

		assertThat(json.get("identificativoMessaggioRicevuta").asText()).isEqualTo("RT-230");
		assertThat(json.get("dataOraMessaggioRicevuta").asText()).isEqualTo("2026-06-24T11:00:00");
	}

	@Test
	void rtSanp240IsConvertedFromReceipt() throws JAXBException {
		CtReceipt receipt = new CtReceipt();
		receipt.setReceiptId("REC-240");
		receipt.setOutcome(StOutcome.OK);
		receipt.setPaymentDateTime(LocalDateTime.of(2026, 6, 24, 9, 30, 0));
		PaSendRTReq req = new PaSendRTReq();
		req.setReceipt(receipt);
		Rpt rpt = rptWith("SANP_240", null, marshal(req, PA_FOR_NODE_PKG));

		JsonNode json = converter.toRtJson(rpt);

		assertThat(json.has("receipt")).isFalse();
		assertThat(json.get("receiptId").asText()).isEqualTo("REC-240");
		assertThat(json.get("outcome").asText()).isEqualTo("OK");
		assertThat(json.get("paymentDateTime").asText()).isEqualTo("2026-06-24T09:30:00");
	}

	@Test
	void rtSanp321IsConvertedFromReceiptV2() throws JAXBException {
		CtReceiptV2 receipt = new CtReceiptV2();
		receipt.setReceiptId("REC-321");
		receipt.setOutcome(StOutcome.OK);
		PaSendRTV2Request req = new PaSendRTV2Request();
		req.setReceipt(receipt);
		Rpt rpt = rptWith("SANP_321_V2", null, marshal(req, PA_FOR_NODE_PKG));

		JsonNode json = converter.toRtJson(rpt);

		assertThat(json.has("receipt")).isFalse();
		assertThat(json.get("receiptId").asText()).isEqualTo("REC-321");
		assertThat(json.get("outcome").asText()).isEqualTo("OK");
	}

	// ---- casi limite --------------------------------------------------------

	@Test
	void rptJsonIsNullWhenXmlRptMissing() {
		assertThat(converter.toRptJson(rptWith("SANP_240", null, new byte[] {1}))).isNull();
	}

	@Test
	void rtJsonIsNullWhenXmlRtMissing() {
		assertThat(converter.toRtJson(rptWith("SANP_240", new byte[] {1}, null))).isNull();
	}

	@Test
	void unknownVersioneRaisesSpeakingError() throws JAXBException {
		byte[] xml = marshal(new RPT(), DIGITPA_PKG);
		Rpt rpt = rptWith("SANP_999", xml, xml);

		assertThatThrownBy(() -> converter.toRptJson(rpt))
				.isInstanceOf(RptRtConversionException.class)
				.hasMessageContaining("SANP_999");
		assertThatThrownBy(() -> converter.toRtJson(rpt))
				.isInstanceOf(RptRtConversionException.class)
				.hasMessageContaining("SANP_999");
	}
}
