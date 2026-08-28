package it.govpay.console.pendenza;

import java.util.List;
import java.util.stream.Stream;

import it.govpay.console.model.StatoPendenza;

/**
 * Fonte unica della traduzione fra {@code stato_versamento} (V1, stringa grezza,
 * genere non garantito nei dati reali) e {@link StatoPendenza} (V2). Usata sia
 * da {@link PendenzaMapper} (mapping in lettura) sia da {@link PendenzaSpecifications}
 * (filtro {@code ?stato=}): le due derivazioni condividono questi gruppi invece di
 * ridefinirli ciascuna per conto proprio, per non poter divergere silenziosamente.
 *
 * <p>{@code ANOMALA} non ha un proprio elenco chiuso di valori grezzi: e' il
 * catch-all per tutto cio' che non rientra negli altri gruppi (compresi i
 * letterali {@code ANOMALA}/{@code ANOMALO} e qualunque valore sconosciuto),
 * esattamente come nel mapper. {@link #ALTRI_STATI_NOTI} espone il complemento,
 * cosi' anche il filtro puo' esprimere lo stesso catch-all con un {@code NOT IN}.
 */
final class StatoVersamentoMapping {

    static final List<String> PAGATA =
            List.of("ESEGUITA", "ESEGUITO", "PAGATA", "PAGATO", "ESEGUITO_ALTRO_CANALE", "ESEGUITO_SENZA_RPT");
    static final List<String> NON_ESEGUITO =
            List.of("NON_ESEGUITA", "NON_ESEGUITO", "NON_PAGATA", "NON_PAGATO");
    static final List<String> PAGATA_PARZIALE = List.of(
            "ESEGUITA_PARZIALE", "ESEGUITO_PARZIALE", "PAGATA_PARZIALE", "PAGATO_PARZIALE", "PARZIALMENTE_ESEGUITO");
    static final List<String> RICONCILIATA =
            List.of("INCASSATA", "INCASSATO", "RICONCILIATA", "RICONCILIATO");
    static final List<String> ANNULLATA = List.of("ANNULLATA", "ANNULLATO");
    static final List<String> SCADUTA_LETTERALE = List.of("SCADUTA", "SCADUTO");
    static final List<String> ANOMALA_LETTERALE = List.of("ANOMALA", "ANOMALO");

    /** Unione di tutti i gruppi diversi da ANOMALA: il complemento per il catch-all. */
    static final List<String> ALTRI_STATI_NOTI = Stream.of(
                    PAGATA, NON_ESEGUITO, PAGATA_PARZIALE, RICONCILIATA, ANNULLATA, SCADUTA_LETTERALE)
            .flatMap(List::stream)
            .toList();

    private StatoVersamentoMapping() {
    }

    /**
     * Mapping diretto, senza la derivazione SCADUTA-da-{@code dataScadenza} (che
     * dipende anche da {@code now} e resta responsabilita' del chiamante: vedi
     * {@link PendenzaMapper#mapStato} per la lettura, {@link PendenzaSpecifications#statoExact}
     * per il filtro).
     */
    static StatoPendenza baseMap(String raw) {
        String normalized = raw.trim().toUpperCase();
        if (PAGATA.contains(normalized)) {
            return StatoPendenza.PAGATA;
        }
        if (NON_ESEGUITO.contains(normalized)) {
            return StatoPendenza.NON_PAGATA;
        }
        if (PAGATA_PARZIALE.contains(normalized)) {
            return StatoPendenza.PAGATA_PARZIALE;
        }
        if (RICONCILIATA.contains(normalized)) {
            return StatoPendenza.RICONCILIATA;
        }
        if (ANNULLATA.contains(normalized)) {
            return StatoPendenza.ANNULLATA;
        }
        if (SCADUTA_LETTERALE.contains(normalized)) {
            return StatoPendenza.SCADUTA;
        }
        return StatoPendenza.ANOMALA;
    }

    /** {@code true} se il valore grezzo rientra in un gruppo esplicito (incluso ANOMALA letterale). */
    static boolean isRiconosciuto(String raw) {
        String normalized = raw.trim().toUpperCase();
        return ALTRI_STATI_NOTI.contains(normalized) || ANOMALA_LETTERALE.contains(normalized);
    }
}
