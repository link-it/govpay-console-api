package it.govpay.console.sla;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.govpay.console.model.SlaKpi;
import it.govpay.console.model.SlaKpiCodice;
import it.govpay.console.model.SlaPeriodo;
import it.govpay.console.model.SlaPunto;
import it.govpay.console.model.SlaResponse;
import it.govpay.console.model.SlaSerieStoricaResponse;
import it.govpay.console.model.SlaStato;
import it.govpay.console.web.BadRequestException;

/**
 * Calcola i KPI di conformità SLA pagoPA interrogando esclusivamente il
 * Prometheus federato (mai il primario: nessun routing multi-sorgente),
 * sulla recording rule {@code job:pa_method_seconds:count}/
 * {@code count_under_2s} — un counter riaggregato per {@code job × metodo},
 * non un {@code rate()}: {@code increase()} su questi resta valido su
 * qualunque periodo storico arbitrario, incluso il mese corrente.
 */
@Service
public class SlaService {

    private static final double SOGLIA_WARNING_PERCENTILE = 95.0;

    /**
     * Oltre questo numero di punti la serie storica viene rifiutata (400):
     * non e' configurabile, e' un tetto tecnico indipendente
     * dall'installazione (a differenza di {@link #granularitaMinutiMinima}).
     */
    private static final int MAX_PUNTI = 500;

    private final PrometheusQueryClient client;
    private final int granularitaMinutiMinima;

    public SlaService(PrometheusQueryClient client,
                      @Value("${govpay.metriche.sla.granularitaMinutiMinima}") int granularitaMinutiMinima) {
        this.client = client;
        this.granularitaMinutiMinima = granularitaMinutiMinima;
    }

    public SlaResponse calcola(LocalDate dataDa, LocalDate dataA) {
        validaPeriodo(dataDa, dataA);

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
        SlaKpi kpi = new SlaKpi()
                .codice(def.codice())
                .metodo(def.metodo())
                .sogliaSecondi(SlaMetodoDefinizione.SOGLIA_SECONDI)
                .sogliaPercentile(SlaMetodoDefinizione.SOGLIA_PERCENTILE);

        Optional<CoppiaValori> coppia = validaCoerenza(
                client.query(totaleQuery(def, range), at),
                client.query(entroSogliaQuery(def, range), at),
                "per " + def.metodo());
        if (coppia.isEmpty()) {
            // Nessun dato nel periodo: WARNING, non OK. A regime non e' normale
            // che un metodo strumentato non riceva traffico: e' un'anomalia da
            // segnalare (core non ancora strumentato, connettivita' Prometheus,
            // periodo richiesto precedente al deploy), non uno stato neutro.
            return kpi.totale(0L).sopraSoglia(0L).conformitaOsservata(null).stato(SlaStato.WARNING);
        }
        long totale = coppia.get().totale();
        long entroSoglia = coppia.get().entroSoglia();
        long sopraSoglia = totale - entroSoglia;
        kpi.totale(totale).sopraSoglia(sopraSoglia);

        if (totale == 0) {
            return kpi.conformitaOsservata(null).stato(SlaStato.WARNING);
        }
        double conformita = (entroSoglia * 100.0) / totale;
        return kpi.conformitaOsservata(conformita).stato(statoPer(conformita));
    }

    public SlaSerieStoricaResponse calcolaSerieStorica(SlaKpiCodice codice, LocalDate dataDa, LocalDate dataA,
                                                        int granularitaMinuti) {
        validaPeriodo(dataDa, dataA);
        if (granularitaMinuti < granularitaMinutiMinima) {
            throw new BadRequestException("'granularitaMinuti' (" + granularitaMinuti + ") e' inferiore al minimo "
                    + "configurato per questa installazione (" + granularitaMinutiMinima + "): il federato non ha "
                    + "una risoluzione piu' fine da offrire davvero.", "granularitaMinuti");
        }

        SlaMetodoDefinizione def = SlaMetodoDefinizione.porCodice(codice);
        Instant from = dataDa.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = dataA.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long stepSeconds = granularitaMinuti * 60L;
        long periodoSeconds = Duration.between(from, to).getSeconds();
        // Il contratto non richiede che granularitaMinuti divida esattamente il
        // periodo (issue #36: solo minimo configurabile e MAX_PUNTI): un resto
        // diventa un ultimo bucket piu' stretto degli altri, non un rifiuto ne'
        // una coda persa. numeroBucketPieni puo' essere 0 (granularita' piu'
        // ampia dell'intero periodo): in quel caso l'unico bucket e' il resto,
        // calcolato come singola instant query esattamente come {@link #calcola}.
        long numeroBucketPieni = periodoSeconds / stepSeconds;
        long restoSeconds = periodoSeconds % stepSeconds;
        long numeroPunti = numeroBucketPieni + (restoSeconds > 0 ? 1 : 0);
        if (numeroPunti > MAX_PUNTI) {
            throw new BadRequestException("Il periodo richiesto con 'granularitaMinuti'=" + granularitaMinuti
                    + " genera " + numeroPunti + " punti, oltre il massimo consentito di " + MAX_PUNTI + ".",
                    "granularitaMinuti");
        }

        List<SlaPunto> serieStorica = new ArrayList<>();
        if (numeroBucketPieni > 0) {
            // Ogni punto e' valutato alla FINE del proprio bucket (increase()
            // guarda indietro di stepSeconds): il primo punto utile e' quindi
            // from+step, non from. L'etichetta esposta in SlaPunto.data resta
            // l'inizio del bucket (timestamp del punto - step).
            Instant queryStart = from.plusSeconds(stepSeconds);
            Instant queryEnd = from.plusSeconds(numeroBucketPieni * stepSeconds);
            String range = stepSeconds + "s";

            Map<Instant, Double> totali = client.queryRange(totaleQuery(def, range), queryStart, queryEnd, stepSeconds);
            Map<Instant, Double> entroSoglia = client.queryRange(
                    entroSogliaQuery(def, range), queryStart, queryEnd, stepSeconds);

            for (long i = 0; i < numeroBucketPieni; i++) {
                Instant timestampValutazione = queryStart.plusSeconds(i * stepSeconds);
                Instant inizioBucket = timestampValutazione.minusSeconds(stepSeconds);
                serieStorica.add(costruisciPunto(inizioBucket, totali.get(timestampValutazione),
                        entroSoglia.get(timestampValutazione)));
            }
        }
        if (restoSeconds > 0) {
            // Bucket finale piu' stretto di granularitaMinuti: query dedicata (non
            // query_range, che richiederebbe uno step uniforme su tutta la serie).
            String rangeResto = restoSeconds + "s";
            Instant inizioBucketResto = to.minusSeconds(restoSeconds);
            Optional<Double> totaleResto = client.query(totaleQuery(def, rangeResto), to);
            Optional<Double> entroSogliaResto = client.query(entroSogliaQuery(def, rangeResto), to);
            serieStorica.add(costruisciPunto(inizioBucketResto, totaleResto.orElse(null), entroSogliaResto.orElse(null)));
        }

        return new SlaSerieStoricaResponse()
                .periodo(new SlaPeriodo().da(dataDa).a(dataA))
                .codice(codice)
                .metodo(def.metodo())
                .granularitaMinuti(granularitaMinuti)
                .sogliaSecondi(SlaMetodoDefinizione.SOGLIA_SECONDI)
                .sogliaPercentile(SlaMetodoDefinizione.SOGLIA_PERCENTILE)
                .serieStorica(serieStorica);
    }

    private static SlaPunto costruisciPunto(Instant inizioBucket, Double totaleValore, Double entroSogliaValore) {
        SlaPunto punto = new SlaPunto().data(OffsetDateTime.ofInstant(inizioBucket, ZoneOffset.UTC));
        Optional<CoppiaValori> coppia = validaCoerenza(Optional.ofNullable(totaleValore),
                Optional.ofNullable(entroSogliaValore), "per il bucket " + inizioBucket);
        if (coppia.isEmpty()) {
            return punto.totale(0L).conformitaOsservata(null);
        }
        long totale = coppia.get().totale();
        long entroSoglia = coppia.get().entroSoglia();
        return punto.totale(totale).conformitaOsservata(totale == 0 ? null : (entroSoglia * 100.0) / totale);
    }

    /**
     * Totale ed entroSoglia vengono da due query indipendenti (istantanee o di
     * range): un esito vuoto e' legittimo solo se ENTRAMBE non hanno un
     * valore (nessun campione in quella finestra). Se ne manca solo una, o se
     * entroSoglia supera il totale, le due risposte non sono coerenti fra
     * loro — non "nessun dato" ma una risposta Prometheus malformata, quindi
     * 502 e non uno zero silenzioso (che produrrebbe conformita' 0% o >100%
     * inventate). Condiviso fra {@link #calcolaKpi} e
     * {@link #costruisciPunto}: stesso rischio, stessa validazione.
     */
    private static Optional<CoppiaValori> validaCoerenza(Optional<Double> totaleValore,
                                                          Optional<Double> entroSogliaValore, String contesto) {
        if (totaleValore.isEmpty() && entroSogliaValore.isEmpty()) {
            return Optional.empty();
        }
        if (totaleValore.isEmpty() || entroSogliaValore.isEmpty()) {
            throw new PrometheusNonRaggiungibileException("Risposta Prometheus incoerente " + contesto
                    + ": solo una delle due serie (totale/entro soglia) ha un valore.", null);
        }
        long totale = Math.round(totaleValore.get());
        long entroSoglia = Math.round(entroSogliaValore.get());
        if (entroSoglia > totale) {
            throw new PrometheusNonRaggiungibileException("Risposta Prometheus incoerente " + contesto
                    + ": entro soglia (" + entroSoglia + ") supera il totale (" + totale + ").", null);
        }
        return Optional.of(new CoppiaValori(totale, entroSoglia));
    }

    private record CoppiaValori(long totale, long entroSoglia) {
    }

    private static void validaPeriodo(LocalDate dataDa, LocalDate dataA) {
        if (dataDa.isAfter(dataA)) {
            throw new BadRequestException("'dataDa' non puo' essere successiva a 'dataA'.");
        }
    }

    private static String totaleQuery(SlaMetodoDefinizione def, String range) {
        return "sum(increase(job:pa_method_seconds:count{metodo=\"" + def.metodo() + "\"}[" + range + "]))";
    }

    // Bucket cumulativo le="2.0": conta le invocazioni ENTRO soglia (Micrometer
    // histogram_bucket standard, preservata cosi' dalla recording rule sul
    // federato), non le violazioni. Il campo di risposta `sopraSoglia` di
    // SlaKpi e' l'opposto (violazioni): si ricava per differenza, non
    // interrogando questo bucket come se fosse gia' "sopraSoglia".
    private static String entroSogliaQuery(SlaMetodoDefinizione def, String range) {
        return "sum(increase(job:pa_method_seconds:count_under_2s{metodo=\"" + def.metodo() + "\"}[" + range + "]))";
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
