package it.govpay.console.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import it.govpay.console.model.SlaKpi;
import it.govpay.console.model.SlaKpiCodice;
import it.govpay.console.model.SlaPunto;
import it.govpay.console.model.SlaResponse;
import it.govpay.console.model.SlaSerieStoricaResponse;
import it.govpay.console.model.SlaStato;
import it.govpay.console.web.BadRequestException;

class SlaServiceTest {

    private static final LocalDate DA = LocalDate.of(2026, 7, 1);
    private static final LocalDate A = LocalDate.of(2026, 7, 31);

    @Mock
    private PrometheusQueryClient client;

    private SlaService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new SlaService(client, 5);
    }

    @Test
    void dataDaSuccessivaADataALanciaBadRequest() {
        assertThatThrownBy(() -> service.calcola(A, DA))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void conformitaSopraSogliaProduceStatoOk() {
        stub("paDemandPaymentNotice", 1000.0, 990.0); // 990 entro soglia su 1000 = 99% >= 98
        stub("paGetPayment", 1000.0, 990.0);
        stub("paSendRT", 1000.0, 990.0);
        stub("paVerifyPaymentNotice", 1000.0, 990.0);

        SlaResponse response = service.calcola(DA, A);

        SlaKpi tdp = kpiPerCodice(response, SlaKpiCodice.TDP);
        assertThat(tdp.getConformitaOsservata().get()).isEqualTo(99.0);
        assertThat(tdp.getStato()).isEqualTo(SlaStato.OK);
        assertThat(tdp.getTotale()).isEqualTo(1000L);
        // sopraSoglia = violazioni = totale - entroSoglia, non il conteggio entro soglia.
        assertThat(tdp.getSopraSoglia()).isEqualTo(10L);
        assertThat(response.getPeriodo().getDa()).isEqualTo(DA);
        assertThat(response.getPeriodo().getA()).isEqualTo(A);
        assertThat(response.getKpi()).hasSize(4);
    }

    @Test
    void conformitaFra95E98ProduceWarning() {
        stub("paDemandPaymentNotice", 1000.0, 960.0); // 96%
        stub("paGetPayment", 1000.0, 960.0);
        stub("paSendRT", 1000.0, 960.0);
        stub("paVerifyPaymentNotice", 1000.0, 960.0);

        SlaKpi tdp = kpiPerCodice(service.calcola(DA, A), SlaKpiCodice.TDP);

        assertThat(tdp.getConformitaOsservata().get()).isEqualTo(96.0);
        assertThat(tdp.getStato()).isEqualTo(SlaStato.WARNING);
        assertThat(tdp.getSopraSoglia()).isEqualTo(40L);
    }

    @Test
    void conformitaSotto95ProduceKo() {
        stub("paDemandPaymentNotice", 1000.0, 900.0); // 90%
        stub("paGetPayment", 1000.0, 900.0);
        stub("paSendRT", 1000.0, 900.0);
        stub("paVerifyPaymentNotice", 1000.0, 900.0);

        SlaKpi tdp = kpiPerCodice(service.calcola(DA, A), SlaKpiCodice.TDP);

        assertThat(tdp.getConformitaOsservata().get()).isEqualTo(90.0);
        assertThat(tdp.getStato()).isEqualTo(SlaStato.KO);
        assertThat(tdp.getSopraSoglia()).isEqualTo(100L);
    }

    /**
     * Nessun dato nel periodo (Prometheus non ha nulla, es. core non ancora
     * strumentato): WARNING, non OK — a regime l'assenza di dati e' anomala.
     */
    @Test
    void nessunDatoProduceWarningConConformitaNull() {
        when(client.query(anyString(), any())).thenReturn(Optional.empty());

        SlaKpi tdp = kpiPerCodice(service.calcola(DA, A), SlaKpiCodice.TDP);

        assertThat(tdp.getConformitaOsservata().get()).isNull();
        assertThat(tdp.getStato()).isEqualTo(SlaStato.WARNING);
        assertThat(tdp.getTotale()).isEqualTo(0L);
        assertThat(tdp.getSopraSoglia()).isEqualTo(0L);
    }

    @Test
    void calcolaConUnaSoloSerieValorizzataLanciaEccezionePrometheus() {
        // Stessa validazione di calcolaSerieStorica: totale presente, entroSoglia
        // assente (o viceversa) non e' "nessun dato", e' una risposta incoerente.
        when(client.query(anyString(), any())).thenReturn(Optional.empty());
        when(client.query(contains("job:pa_method_seconds:count{metodo=\"paDemandPaymentNotice\"}"), any()))
                .thenReturn(Optional.of(100.0));

        assertThatThrownBy(() -> service.calcola(DA, A))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void calcolaConEntroSogliaMaggioreDelTotaleLanciaEccezionePrometheus() {
        when(client.query(anyString(), any())).thenReturn(Optional.empty());
        when(client.query(contains("job:pa_method_seconds:count{metodo=\"paDemandPaymentNotice\"}"), any()))
                .thenReturn(Optional.of(100.0));
        when(client.query(contains("job:pa_method_seconds:count_under_2s{metodo=\"paDemandPaymentNotice\"}"), any()))
                .thenReturn(Optional.of(150.0));

        assertThatThrownBy(() -> service.calcola(DA, A))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void tuttiI4MetodiSonoPresentiConSoglieCorrette() {
        when(client.query(anyString(), any())).thenReturn(Optional.empty());

        SlaResponse response = service.calcola(DA, A);

        assertThat(response.getKpi()).extracting(SlaKpi::getCodice)
                .containsExactlyInAnyOrder(SlaKpiCodice.TDP, SlaKpiCodice.TGP, SlaKpiCodice.TSRT, SlaKpiCodice.TVP);
        assertThat(response.getKpi()).allSatisfy(kpi -> {
            assertThat(kpi.getSogliaSecondi()).isEqualTo(2.0);
            assertThat(kpi.getSogliaPercentile()).isEqualTo(98);
        });
    }

    @Test
    void serieStoricaDataDaSuccessivaADataALanciaBadRequest() {
        assertThatThrownBy(() -> service.calcolaSerieStorica(SlaKpiCodice.TDP, A, DA, 360))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void serieStoricaGranularitaSottoIlMinimoLanciaBadRequestSulCampo() {
        assertThatThrownBy(() -> service.calcolaSerieStorica(SlaKpiCodice.TDP, DA, DA, 4))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getField()).isEqualTo("granularitaMinuti"));
    }

    @Test
    void serieStoricaOltreMaxPuntiLanciaBadRequestSulCampo() {
        // 31 giorni a 5 minuti = 8928 punti, ben oltre MAX_PUNTI=500.
        assertThatThrownBy(() -> service.calcolaSerieStorica(SlaKpiCodice.TDP, DA, A, 5))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getField()).isEqualTo("granularitaMinuti"));
    }

    /**
     * Un giorno (86400s) con granularita'=1000' (60000s) non divide esattamente:
     * il contratto (issue #36) non lo vieta (solo minimo configurabile e
     * MAX_PUNTI), quindi l'ultimo bucket e' semplicemente piu' stretto
     * (26400s = 7h20m) invece di essere rifiutato o troncato in silenzio -
     * calcolato con una instant query dedicata, non query_range (che
     * richiede uno step uniforme su tutta la serie).
     */
    @Test
    void serieStoricaConGranularitaCheNonDivideIlPeriodoProduceUnUltimoBucketParziale() {
        Instant fineBucketPieno = Instant.parse("2026-07-01T16:40:00Z"); // from + 60000s
        Instant fineGiorno = Instant.parse("2026-07-02T00:00:00Z"); // to

        when(client.queryRange(contains("job:pa_method_seconds:count{metodo=\"paDemandPaymentNotice\"}"),
                any(), any(), anyLong()))
                .thenReturn(Map.of(fineBucketPieno, 1000.0));
        when(client.queryRange(contains("job:pa_method_seconds:count_under_2s{metodo=\"paDemandPaymentNotice\"}"),
                any(), any(), anyLong()))
                .thenReturn(Map.of(fineBucketPieno, 990.0));
        when(client.query(contains("job:pa_method_seconds:count{metodo=\"paDemandPaymentNotice\"}[26400s]"), eq(fineGiorno)))
                .thenReturn(Optional.of(200.0));
        when(client.query(contains("job:pa_method_seconds:count_under_2s{metodo=\"paDemandPaymentNotice\"}[26400s]"), eq(fineGiorno)))
                .thenReturn(Optional.of(180.0));

        SlaSerieStoricaResponse response = service.calcolaSerieStorica(SlaKpiCodice.TDP, DA, DA, 1000);

        assertThat(response.getSerieStorica()).hasSize(2);
        SlaPunto bucketPieno = response.getSerieStorica().get(0);
        assertThat(bucketPieno.getData().toInstant()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(bucketPieno.getTotale()).isEqualTo(1000L);

        SlaPunto bucketParziale = response.getSerieStorica().get(1);
        assertThat(bucketParziale.getData().toInstant()).isEqualTo(fineBucketPieno);
        assertThat(bucketParziale.getTotale()).isEqualTo(200L);
        assertThat(bucketParziale.getConformitaOsservata().get()).isCloseTo(90.0, org.assertj.core.data.Offset.offset(0.001));
    }

    /**
     * Granularita' piu' ampia dell'intero periodo: numeroBucketPieni=0, l'intero
     * periodo diventa l'unico bucket (il "resto"), calcolato con la stessa
     * forma a instant query di {@link #calcola} - mai una query_range con
     * start successivo a end.
     */
    @Test
    void serieStoricaConGranularitaPiuAmpiaDelPeriodoProduceUnSoloBucketViaInstantQuery() {
        when(client.query(contains("job:pa_method_seconds:count{metodo=\"paDemandPaymentNotice\"}[86400s]"), any()))
                .thenReturn(Optional.of(500.0));
        when(client.query(contains("job:pa_method_seconds:count_under_2s{metodo=\"paDemandPaymentNotice\"}[86400s]"), any()))
                .thenReturn(Optional.of(495.0));

        SlaSerieStoricaResponse response = service.calcolaSerieStorica(SlaKpiCodice.TDP, DA, DA, 60 * 25);

        assertThat(response.getSerieStorica()).hasSize(1);
        SlaPunto unico = response.getSerieStorica().get(0);
        assertThat(unico.getData().toInstant()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(unico.getTotale()).isEqualTo(500L);
        verify(client, never()).queryRange(anyString(), any(), any(), anyLong());
    }

    @Test
    void serieStoricaConUnaSoloSerieValorizzataLanciaEccezionePrometheus() {
        // t06 presente solo in "totali", assente in "entroSoglia": le due query
        // indipendenti sono incoerenti fra loro, non un bucket vuoto.
        Instant t06 = Instant.parse("2026-07-01T06:00:00Z");
        Instant t12 = Instant.parse("2026-07-01T12:00:00Z");
        Instant t18 = Instant.parse("2026-07-01T18:00:00Z");
        Instant t24 = Instant.parse("2026-07-02T00:00:00Z");

        when(client.queryRange(contains("job:pa_method_seconds:count{metodo=\"paDemandPaymentNotice\"}"),
                any(), any(), anyLong()))
                .thenReturn(Map.of(t06, 100.0, t12, 0.0, t18, 0.0, t24, 0.0));
        when(client.queryRange(contains("job:pa_method_seconds:count_under_2s{metodo=\"paDemandPaymentNotice\"}"),
                any(), any(), anyLong()))
                .thenReturn(Map.of(t12, 0.0, t18, 0.0, t24, 0.0));

        assertThatThrownBy(() -> service.calcolaSerieStorica(SlaKpiCodice.TDP, DA, DA, 360))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void serieStoricaConEntroSogliaMaggioreDelTotaleLanciaEccezionePrometheus() {
        Instant t06 = Instant.parse("2026-07-01T06:00:00Z");
        Instant t12 = Instant.parse("2026-07-01T12:00:00Z");
        Instant t18 = Instant.parse("2026-07-01T18:00:00Z");
        Instant t24 = Instant.parse("2026-07-02T00:00:00Z");

        when(client.queryRange(contains("job:pa_method_seconds:count{metodo=\"paDemandPaymentNotice\"}"),
                any(), any(), anyLong()))
                .thenReturn(Map.of(t06, 100.0, t12, 0.0, t18, 0.0, t24, 0.0));
        when(client.queryRange(contains("job:pa_method_seconds:count_under_2s{metodo=\"paDemandPaymentNotice\"}"),
                any(), any(), anyLong()))
                .thenReturn(Map.of(t06, 150.0, t12, 0.0, t18, 0.0, t24, 0.0));

        assertThatThrownBy(() -> service.calcolaSerieStorica(SlaKpiCodice.TDP, DA, DA, 360))
                .isInstanceOf(PrometheusNonRaggiungibileException.class);
    }

    @Test
    void serieStoricaCostruisceUnPuntoPerBucketConGapATotaleZero() {
        // 1 giorno, granularita' 360' (6h) = 4 bucket, come l'esempio della issue:
        // [00-06, 06-12, 12-18, 18-24]. Ogni punto e' valutato alla FINE del
        // bucket (query_range + increase guardano indietro): il bucket 06-12
        // (nessun dato in mock, quindi "assente") deve comunque comparire come
        // totale=0/conformitaOsservata=null, non essere omesso.
        Instant t06 = Instant.parse("2026-07-01T06:00:00Z");
        Instant t12 = Instant.parse("2026-07-01T12:00:00Z");
        Instant t18 = Instant.parse("2026-07-01T18:00:00Z");
        Instant t24 = Instant.parse("2026-07-02T00:00:00Z");

        when(client.queryRange(contains("job:pa_method_seconds:count{metodo=\"paDemandPaymentNotice\"}"),
                any(), any(), anyLong()))
                .thenReturn(Map.of(t06, 1842.0, t18, 5310.0, t24, 2977.0));
        when(client.queryRange(contains("job:pa_method_seconds:count_under_2s{metodo=\"paDemandPaymentNotice\"}"),
                any(), any(), anyLong()))
                .thenReturn(Map.of(t06, 1828.0, t18, 5184.0, t24, 2942.0));

        SlaSerieStoricaResponse response = service.calcolaSerieStorica(SlaKpiCodice.TDP, DA, DA, 360);

        assertThat(response.getCodice()).isEqualTo(SlaKpiCodice.TDP);
        assertThat(response.getMetodo()).isEqualTo("paDemandPaymentNotice");
        assertThat(response.getGranularitaMinuti()).isEqualTo(360);
        assertThat(response.getSerieStorica()).hasSize(4);

        SlaPunto bucket0 = response.getSerieStorica().get(0);
        assertThat(bucket0.getData().toInstant()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(bucket0.getTotale()).isEqualTo(1842L);
        assertThat(bucket0.getConformitaOsservata().get()).isCloseTo(99.24, org.assertj.core.data.Offset.offset(0.01));

        SlaPunto bucketVuoto = response.getSerieStorica().get(1);
        assertThat(bucketVuoto.getData().toInstant()).isEqualTo(t06);
        assertThat(bucketVuoto.getTotale()).isEqualTo(0L);
        assertThat(bucketVuoto.getConformitaOsservata().get()).isNull();

        SlaPunto bucket3 = response.getSerieStorica().get(3);
        assertThat(bucket3.getData().toInstant()).isEqualTo(t18);
        assertThat(bucket3.getTotale()).isEqualTo(2977L);
    }

    /** {@code entroSoglia}: risultato della serie {@code ..._count_under_2s} (invocazioni entro soglia, non violazioni). */
    private void stub(String metodo, double totale, double entroSoglia) {
        when(client.query(contains("job:pa_method_seconds:count{metodo=\"" + metodo + "\"}"), any()))
                .thenReturn(Optional.of(totale));
        when(client.query(contains("job:pa_method_seconds:count_under_2s{metodo=\"" + metodo + "\""), any()))
                .thenReturn(Optional.of(entroSoglia));
    }

    private SlaKpi kpiPerCodice(SlaResponse response, SlaKpiCodice codice) {
        return response.getKpi().stream()
                .filter(k -> k.getCodice() == codice)
                .findFirst()
                .orElseThrow();
    }
}
