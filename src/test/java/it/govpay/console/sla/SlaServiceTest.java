package it.govpay.console.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import it.govpay.console.model.SlaKpi;
import it.govpay.console.model.SlaKpiCodice;
import it.govpay.console.model.SlaResponse;
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
        service = new SlaService(client);
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

    /** {@code entroSoglia}: risultato del bucket Prometheus {@code le="2.0"} (invocazioni entro soglia, non violazioni). */
    private void stub(String metodo, double totale, double entroSoglia) {
        when(client.query(contains("govpay_pa_method_seconds_count{metodo=\"" + metodo + "\"}"), any()))
                .thenReturn(Optional.of(totale));
        when(client.query(contains("govpay_pa_method_seconds_bucket{metodo=\"" + metodo + "\""), any()))
                .thenReturn(Optional.of(entroSoglia));
    }

    private SlaKpi kpiPerCodice(SlaResponse response, SlaKpiCodice codice) {
        return response.getKpi().stream()
                .filter(k -> k.getCodice() == codice)
                .findFirst()
                .orElseThrow();
    }
}
