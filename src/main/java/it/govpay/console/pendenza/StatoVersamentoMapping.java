package it.govpay.console.pendenza;

import java.util.List;
import java.util.stream.Stream;

import it.govpay.console.model.StatoPendenza;

/**
 * Fonte unica della traduzione fra {@code stato_versamento} (stringa grezza,
 * genere non garantito nei dati reali) e {@link StatoPendenza}. Usata sia
 * da {@link PendenzaMapper} (mapping in lettura) sia da {@link PendenzaSpecifications}
 * (filtro {@code ?stato=}): le due derivazioni condividono questi gruppi invece di
 * ridefinirli ciascuna per conto proprio, per non poter divergere silenziosamente.
 *
 * <p>{@code ANOMALO} non ha un proprio elenco chiuso di valori grezzi: e' il
 * catch-all per tutto cio' che non rientra negli altri gruppi (compresi i
 * letterali {@code ANOMALA}/{@code ANOMALO} e qualunque valore sconosciuto),
 * esattamente come nel mapper. {@link #ALTRI_STATI_NOTI} espone il complemento,
 * cosi' anche il filtro puo' esprimere lo stesso catch-all con un {@code NOT IN}.
 */
final class StatoVersamentoMapping {

    static final List<String> ESEGUITO = List.of("ESEGUITA", "ESEGUITO", "PAGATA", "PAGATO");
    static final List<String> ESEGUITO_ALTRO_CANALE = List.of("ESEGUITO_ALTRO_CANALE");
    static final List<String> ESEGUITO_SENZA_RPT = List.of("ESEGUITO_SENZA_RPT");
    static final List<String> NON_ESEGUITO =
            List.of("NON_ESEGUITA", "NON_ESEGUITO", "NON_PAGATA", "NON_PAGATO");
    static final List<String> PARZIALMENTE_ESEGUITO = List.of(
            "ESEGUITA_PARZIALE", "ESEGUITO_PARZIALE", "PAGATA_PARZIALE", "PAGATO_PARZIALE", "PARZIALMENTE_ESEGUITO");
    static final List<String> INCASSATO =
            List.of("INCASSATA", "INCASSATO", "RICONCILIATA", "RICONCILIATO");
    static final List<String> ANNULLATO = List.of("ANNULLATA", "ANNULLATO");
    static final List<String> SCADUTO_LETTERALE = List.of("SCADUTA", "SCADUTO");
    static final List<String> ANOMALO_LETTERALE = List.of("ANOMALA", "ANOMALO");

    /** Unione di tutti i gruppi diversi da ANOMALO: il complemento per il catch-all. */
    static final List<String> ALTRI_STATI_NOTI = Stream.of(
                    ESEGUITO, ESEGUITO_ALTRO_CANALE, ESEGUITO_SENZA_RPT, NON_ESEGUITO,
                    PARZIALMENTE_ESEGUITO, INCASSATO, ANNULLATO, SCADUTO_LETTERALE)
            .flatMap(List::stream)
            .toList();

    private StatoVersamentoMapping() {
    }

    /**
     * Mapping diretto, senza la derivazione SCADUTO-da-{@code dataScadenza} (che
     * dipende anche da {@code now} e resta responsabilita' del chiamante: vedi
     * {@link PendenzaMapper#mapStato} per la lettura, {@link PendenzaSpecifications#statoExact}
     * per il filtro).
     */
    static StatoPendenza baseMap(String raw) {
        String normalized = raw.trim().toUpperCase();
        if (ESEGUITO.contains(normalized)) {
            return StatoPendenza.ESEGUITO;
        }
        if (ESEGUITO_ALTRO_CANALE.contains(normalized)) {
            return StatoPendenza.ESEGUITO_ALTRO_CANALE;
        }
        if (ESEGUITO_SENZA_RPT.contains(normalized)) {
            return StatoPendenza.ESEGUITO_SENZA_RPT;
        }
        if (NON_ESEGUITO.contains(normalized)) {
            return StatoPendenza.NON_ESEGUITO;
        }
        if (PARZIALMENTE_ESEGUITO.contains(normalized)) {
            return StatoPendenza.PARZIALMENTE_ESEGUITO;
        }
        if (INCASSATO.contains(normalized)) {
            return StatoPendenza.INCASSATO;
        }
        if (ANNULLATO.contains(normalized)) {
            return StatoPendenza.ANNULLATO;
        }
        if (SCADUTO_LETTERALE.contains(normalized)) {
            return StatoPendenza.SCADUTO;
        }
        return StatoPendenza.ANOMALO;
    }

    /** {@code true} se il valore grezzo rientra in un gruppo esplicito (incluso ANOMALO letterale). */
    static boolean isRiconosciuto(String raw) {
        String normalized = raw.trim().toUpperCase();
        return ALTRI_STATI_NOTI.contains(normalized) || ANOMALO_LETTERALE.contains(normalized);
    }
}
