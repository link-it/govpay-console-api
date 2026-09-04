package it.govpay.console.sla;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;

import it.govpay.console.model.SlaKpi;
import it.govpay.console.model.SlaPeriodo;
import it.govpay.console.model.SlaResponse;
import it.govpay.console.model.SlaStato;
import it.govpay.console.web.BadRequestException;

/**
 * Calcola i 4 KPI di conformità SLA pagoPA interrogando
 * Prometheus con una singola instant query per serie, valutata a
 * {@code time=dataA} con range pari alla durata dell'intero periodo — non
 * {@code rate(...[5m])} (quello serve a dashboard live su finestre corte,
 * non a un periodo storico arbitrario).
 */
@Service
public class SlaService {

    private static final double SOGLIA_WARNING_PERCENTILE = 95.0;

    private final PrometheusQueryClient client;

    public SlaService(PrometheusQueryClient client) {
        this.client = client;
    }

    public SlaResponse calcola(LocalDate dataDa, LocalDate dataA) {
        if (dataDa.isAfter(dataA)) {
            throw new BadRequestException("'dataDa' non puo' essere successiva a 'dataA'.");
        }

        Instant from = dataDa.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = dataA.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        String range = Duration.between(from, to).getSeconds() + "s";

        List<SlaKpi> kpi = List.of(SlaMetodoDefinizione.values()).stream()
                .map(def -> calcolaKpi(def, range, to))
                .toList();

        return new SlaResponse()
                .periodo(new SlaPeriodo().da(dataDa).a(dataA))
                .kpi(kpi);
    }

    private SlaKpi calcolaKpi(SlaMetodoDefinizione def, String range, Instant at) {
        String totaleQuery = "sum(increase(govpay_pa_method_seconds_count{metodo=\""
                + def.metodo() + "\"}[" + range + "]))";
        // Bucket cumulativo le="2.0": conta le invocazioni ENTRO soglia (Micrometer
        // histogram_bucket standard), non le violazioni. Il campo di risposta
        // `sopraSoglia` e' l'opposto (violazioni): si ricava per differenza, non
        // interrogando direttamente questo bucket come se fosse gia' "sopraSoglia".
        String entroSogliaQuery = "sum(increase(govpay_pa_method_seconds_bucket{metodo=\""
                + def.metodo() + "\",le=\"" + SlaMetodoDefinizione.SOGLIA_SECONDI + "\"}[" + range + "]))";

        long totale = Math.round(client.query(totaleQuery, at).orElse(0.0));
        long entroSoglia = Math.round(client.query(entroSogliaQuery, at).orElse(0.0));
        long sopraSoglia = totale - entroSoglia;

        SlaKpi kpi = new SlaKpi()
                .codice(def.codice())
                .metodo(def.metodo())
                .sogliaSecondi(SlaMetodoDefinizione.SOGLIA_SECONDI)
                .sogliaPercentile(SlaMetodoDefinizione.SOGLIA_PERCENTILE)
                .totale(totale)
                .sopraSoglia(sopraSoglia);

        if (totale == 0) {
            // Nessun dato nel periodo: WARNING, non OK. A regime non e' normale
            // che un metodo strumentato non riceva traffico: e' un'anomalia da
            // segnalare (core non ancora strumentato, connettivita' Prometheus,
            // periodo richiesto precedente al deploy), non uno stato neutro.
            return kpi.conformitaOsservata(null).stato(SlaStato.WARNING);
        }
        double conformita = (entroSoglia * 100.0) / totale;
        return kpi.conformitaOsservata(conformita).stato(statoPer(conformita));
    }

    private SlaStato statoPer(double conformitaOsservata) {
        if (conformitaOsservata >= SlaMetodoDefinizione.SOGLIA_PERCENTILE) {
            return SlaStato.OK;
        }
        if (conformitaOsservata >= SOGLIA_WARNING_PERCENTILE) {
            return SlaStato.WARNING;
        }
        return SlaStato.KO;
    }
}
