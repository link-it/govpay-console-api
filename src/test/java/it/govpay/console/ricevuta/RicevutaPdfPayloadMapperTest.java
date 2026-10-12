package it.govpay.console.ricevuta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import it.gov.digitpa.schemas._2011.pagamenti.CtDatiSingoloPagamentoRT;
import it.gov.digitpa.schemas._2011.pagamenti.CtDatiVersamentoRT;
import it.gov.digitpa.schemas._2011.pagamenti.CtDominio;
import it.gov.digitpa.schemas._2011.pagamenti.CtIdentificativoUnivocoPersonaFG;
import it.gov.digitpa.schemas._2011.pagamenti.CtSoggettoPagatore;
import it.gov.digitpa.schemas._2011.pagamenti.RPT;
import it.gov.digitpa.schemas._2011.pagamenti.RT;
import it.gov.digitpa.schemas._2011.pagamenti.StTipoIdentificativoUnivocoPersFG;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.Versamento;
import it.govpay.stampe.client.model.Receipt;
import it.govpay.stampe.client.model.ReceiptItem;
import it.govpay.stampe.client.model.ReceiptItemStatus;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

/**
 * Verifica che {@link RicevutaPdfPayloadMapper} legga data operazione/applicativa, soggetto
 * pagatore e voci dall'XML storico (RT, e per SANP_230 anche RPT) — non dai dati correnti
 * della pendenza — per tutti e tre i formati RT gestiti.
 */
class RicevutaPdfPayloadMapperTest {

    private final RicevutaPdfPayloadMapper mapper = new RicevutaPdfPayloadMapper("");

    // ---------- SANP_230 (ctRicevutaTelematica) ----------

    @Test
    void sanp230LeggeDataOperazioneDallRptEDataApplicativaDallaRt() throws JAXBException {
        Rpt rpt = rptSanp230(true);

        Receipt receipt = mapper.toReceipt(rpt);

        // RPT.dataOraMessaggioRichiesta = 2026-06-09T08:15 -> non mezzanotte, ore/minuti visibili
        assertThat(receipt.getOperationDate()).isEqualTo("09/06/2026 08:15");
        // RT.dataOraMessaggioRicevuta = 2026-06-10T09:32 -> sempre solo data
        assertThat(receipt.getApplicationDate()).isEqualTo("10/06/2026");
    }

    @Test
    void sanp230SenzaXmlRptRipiegaSullaColonnaDataMsgRichiesta() throws JAXBException {
        Rpt rpt = rptSanp230(false);
        // mezzanotte esatta -> la data operazione deve mostrare solo la data
        rpt.setDataMsgRichiesta(OffsetDateTime.of(2026, 6, 9, 0, 0, 0, 0, ZoneOffset.ofHours(2)));

        Receipt receipt = mapper.toReceipt(rpt);

        assertThat(receipt.getOperationDate()).isEqualTo("09/06/2026");
    }

    @Test
    void sanp230UsaIlSoggettoPagatoreDellaRtNonIlDebitoreCorrenteDellaPendenza() throws JAXBException {
        Rpt rpt = rptSanp230(true);

        Receipt receipt = mapper.toReceipt(rpt);

        assertThat(receipt.getPayer().getFiscalCode()).isEqualTo("RSSMRA80A01H501U");
        assertThat(receipt.getPayer().getFullName()).isEqualTo("Mario Rossi (storico RT)");
        assertThat(receipt.getPayer().getAddress()).isEqualTo("Via Roma, 1");
        assertThat(receipt.getPayer().getLocation()).isEqualTo("00100 Roma (RM)");
    }

    @Test
    void sanp230UsaLeVociDellaRtConIurVeroEStatoPerImporto() throws JAXBException {
        Rpt rpt = rptSanp230(true);

        Receipt receipt = mapper.toReceipt(rpt);

        assertThat(receipt.getItems()).hasSize(2);
        ReceiptItem eseguita = receipt.getItems().get(0);
        assertThat(eseguita.getIur()).isEqualTo("IUR900000004");
        assertThat(eseguita.getDescription()).isEqualTo("Diritti di segreteria");
        assertThat(eseguita.getAmount()).isEqualTo(16.00);
        assertThat(eseguita.getStatus()).isEqualTo(ReceiptItemStatus.EXECUTED);

        ReceiptItem nonEseguita = receipt.getItems().get(1);
        assertThat(nonEseguita.getIur()).isEqualTo("IUR900000005");
        assertThat(nonEseguita.getAmount()).isEqualTo(0.0);
        assertThat(nonEseguita.getStatus()).isEqualTo(ReceiptItemStatus.NOT_EXECUTED);
    }

    private static Rpt rptSanp230(boolean conXmlRpt) throws JAXBException {
        RT rt = new RT();
        rt.setVersioneOggetto("6.2");
        CtDominio dominioRt = new CtDominio();
        dominioRt.setIdentificativoDominio("12345678901");
        rt.setDominio(dominioRt);
        rt.setIdentificativoMessaggioRicevuta("RT900000004");
        rt.setDataOraMessaggioRicevuta(LocalDateTime.of(2026, 6, 10, 9, 32, 0));

        CtIdentificativoUnivocoPersonaFG idPagatore = new CtIdentificativoUnivocoPersonaFG();
        idPagatore.setTipoIdentificativoUnivoco(StTipoIdentificativoUnivocoPersFG.F);
        idPagatore.setCodiceIdentificativoUnivoco("RSSMRA80A01H501U");
        CtSoggettoPagatore pagatore = new CtSoggettoPagatore();
        pagatore.setIdentificativoUnivocoPagatore(idPagatore);
        pagatore.setAnagraficaPagatore("Mario Rossi (storico RT)");
        pagatore.setIndirizzoPagatore("Via Roma");
        pagatore.setCivicoPagatore("1");
        pagatore.setCapPagatore("00100");
        pagatore.setLocalitaPagatore("Roma");
        pagatore.setProvinciaPagatore("RM");
        rt.setSoggettoPagatore(pagatore);

        CtDatiVersamentoRT dati = new CtDatiVersamentoRT();
        dati.setCodiceEsitoPagamento("2");
        dati.setImportoTotalePagato(new BigDecimal("16.00"));
        dati.setIdentificativoUnivocoVersamento("IUV900000004");
        dati.setCodiceContestoPagamento("CCP900000004");

        CtDatiSingoloPagamentoRT eseguita = new CtDatiSingoloPagamentoRT();
        eseguita.setSingoloImportoPagato(new BigDecimal("16.00"));
        eseguita.setIdentificativoUnivocoRiscossione("IUR900000004");
        eseguita.setCausaleVersamento("Diritti di segreteria");
        dati.getDatiSingoloPagamentos().add(eseguita);

        CtDatiSingoloPagamentoRT nonEseguita = new CtDatiSingoloPagamentoRT();
        nonEseguita.setSingoloImportoPagato(BigDecimal.ZERO);
        nonEseguita.setIdentificativoUnivocoRiscossione("IUR900000005");
        nonEseguita.setCausaleVersamento("Sanzione non applicata");
        dati.getDatiSingoloPagamentos().add(nonEseguita);

        rt.setDatiPagamento(dati);

        Rpt rpt = new Rpt();
        rpt.setIuv("IUV900000004");
        rpt.setCcp("CCP900000004");
        rpt.setCodDominio("12345678901");
        rpt.setVersione("SANP_230");
        rpt.setCodEsitoPagamento(2);
        rpt.setImportoTotalePagato(16.00);
        // Diversa dalla data vera nella RPT: se il mapper ripiegasse comunque su questa
        // colonna anche con xml_rpt presente, il test del caso "con xmlRpt" lo scoprirebbe.
        rpt.setDataMsgRichiesta(OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        rpt.setXmlRt(marshal(RT.class, rt));

        if (conXmlRpt) {
            RPT rpTelematico = new RPT();
            rpTelematico.setDataOraMessaggioRichiesta(LocalDateTime.of(2026, 6, 9, 8, 15, 0));
            rpt.setXmlRpt(marshal(RPT.class, rpTelematico));
        }

        Versamento v = new Versamento();
        v.setCausaleVersamento("Causale versamento corrente (non e' la voce della ricevuta)");
        // Debitore corrente deliberatamente diverso dal soggetto pagatore storico della RT:
        // se il mapper tornasse a leggerlo da qui, i test sul payer lo scoprirebbero.
        v.setDebitoreAnagrafica("Debitore attuale, diverso da quello storico");
        v.setDebitoreIdentificativo("DBTATT00A00A000A");
        Dominio d = new Dominio();
        d.setCodDominio("12345678901");
        d.setRagioneSociale("Comune di Test");
        v.setDominio(d);
        rpt.setVersamento(v);

        return rpt;
    }

    // ---------- ctReceipt (SANP_240) ----------

    /** La fixture non porta {@code paymentDateTime}/{@code applicationDate}: "N/D", non un valore dal DB. */
    @Test
    void ctReceiptSenzaDateNellaRtNonRipiegaSulleColonneDbMaMostraNd() throws IOException {
        Rpt rpt = rptCtReceipt();

        Receipt receipt = mapper.toReceipt(rpt);

        assertThat(receipt.getOperationDate()).isEqualTo("N/D");
        assertThat(receipt.getApplicationDate()).isEqualTo("N/D");
    }

    @Test
    void ctReceiptUsaIlDebtorDellaRtNonIlPayerNeIlDebitoreCorrente() throws IOException {
        Rpt rpt = rptCtReceipt();

        Receipt receipt = mapper.toReceipt(rpt);

        assertThat(receipt.getPayer().getFullName()).isEqualTo("Mario Rossi");
        assertThat(receipt.getPayer().getFiscalCode()).isEqualTo("RSSMRA80A01H501U");
    }

    @Test
    void ctReceiptUsaLeVociDellaRtConReceiptIdComeIur() throws IOException {
        Rpt rpt = rptCtReceipt();

        Receipt receipt = mapper.toReceipt(rpt);

        assertThat(receipt.getItems()).hasSize(1);
        ReceiptItem item = receipt.getItems().get(0);
        assertThat(item.getIur()).isEqualTo("RT900000003");
        assertThat(item.getDescription()).isEqualTo("TARI 2026 saldo");
        assertThat(item.getAmount()).isEqualTo(10.00);
        assertThat(item.getStatus()).isEqualTo(ReceiptItemStatus.EXECUTED);
    }

    private static Rpt rptCtReceipt() throws IOException {
        Rpt rpt = new Rpt();
        rpt.setIuv("IUV900000003");
        rpt.setCcp("RT900000003");
        rpt.setCodDominio("12345678901");
        rpt.setVersione("SANP_240");
        rpt.setCodEsitoPagamento(0);
        rpt.setImportoTotalePagato(10.00);
        rpt.setDataMsgRichiesta(OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        rpt.setXmlRt(risorsa("rt-v2-ok.xml"));

        Versamento v = new Versamento();
        v.setCausaleVersamento("Causale versamento corrente");
        v.setDebitoreAnagrafica("Debitore attuale, diverso dal debtor della RT");
        Dominio d = new Dominio();
        d.setCodDominio("12345678901");
        d.setRagioneSociale("Comune di Prova");
        v.setDominio(d);
        rpt.setVersamento(v);

        return rpt;
    }

    // ---------- ctReceiptV2 (SANP 3.2.1 V2) ----------

    @Test
    void ctReceiptV2LeggeDataOperazioneEApplicativaDalPaymentDateTimeEApplicationDateDellaRt()
            throws IOException {
        Rpt rpt = rptCtReceiptV2();

        Receipt receipt = mapper.toReceipt(rpt);

        // paymentDateTime = 2026-09-01T10:00:00+02:00 -> non mezzanotte, ore/minuti visibili
        assertThat(receipt.getOperationDate()).isEqualTo("01/09/2026 10:00");
        // applicationDate = 2026-09-01 -> sempre solo data
        assertThat(receipt.getApplicationDate()).isEqualTo("01/09/2026");
    }

    @Test
    void ctReceiptV2UsaIlDebtorDellaRtConIndirizzo() throws IOException {
        Rpt rpt = rptCtReceiptV2();

        Receipt receipt = mapper.toReceipt(rpt);

        assertThat(receipt.getPayer().getFullName()).isEqualTo("Mario Rossi");
        assertThat(receipt.getPayer().getAddress()).isEqualTo("Via Roma 1");
        assertThat(receipt.getPayer().getLocation()).isEqualTo("00100 Roma (RM)");
    }

    @Test
    void ctReceiptV2UsaLeVociDellaRtConReceiptIdComeIur() throws IOException {
        Rpt rpt = rptCtReceiptV2();

        Receipt receipt = mapper.toReceipt(rpt);

        assertThat(receipt.getItems()).hasSize(1);
        ReceiptItem item = receipt.getItems().get(0);
        assertThat(item.getIur()).isEqualTo("RT900000001");
        assertThat(item.getDescription()).isEqualTo("TARI 2026 saldo");
        assertThat(item.getAmount()).isEqualTo(10.00);
        assertThat(item.getStatus()).isEqualTo(ReceiptItemStatus.EXECUTED);
    }

    private static Rpt rptCtReceiptV2() throws IOException {
        Rpt rpt = new Rpt();
        rpt.setIuv("IUV900000001");
        rpt.setCcp("RT900000001");
        rpt.setCodDominio("12345678901");
        rpt.setVersione("SANP_321_V2");
        rpt.setCodEsitoPagamento(0);
        rpt.setImportoTotalePagato(10.00);
        rpt.setDataMsgRichiesta(OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        rpt.setXmlRt(risorsa("rt-v2_2-ok.xml"));

        Versamento v = new Versamento();
        v.setCausaleVersamento("Causale versamento corrente");
        v.setDebitoreAnagrafica("Debitore attuale, diverso dal debtor della RT");
        Dominio d = new Dominio();
        d.setCodDominio("12345678901");
        d.setRagioneSociale("Comune di Prova");
        v.setDominio(d);
        rpt.setVersamento(v);

        return rpt;
    }

    // ---------- helpers ----------

    private static byte[] marshal(Class<?> type, Object value) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(type);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        context.createMarshaller().marshal(value, baos);
        return baos.toByteArray();
    }

    private static byte[] risorsa(String nome) throws IOException {
        try (InputStream in = RicevutaPdfPayloadMapperTest.class.getClassLoader().getResourceAsStream(nome)) {
            return in.readAllBytes();
        }
    }
}
