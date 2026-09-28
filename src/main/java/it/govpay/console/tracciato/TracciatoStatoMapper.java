package it.govpay.console.tracciato;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.govpay.console.model.StatoTracciatoPendenza;

/**
 * Combina lo stato grezzo V1 ({@code tracciati.stato}: {@code ELABORAZIONE},
 * {@code COMPLETATO}, {@code SCARTATO}, {@code IN_STAMPA}) con lo step
 * fine-grain ({@code bean_dati.stepElaborazione}: {@code NUOVO},
 * {@code IN_CARICAMENTO}, {@code CARICAMENTO_OK}, {@code CARICAMENTO_KO}, ...)
 * nello stato REST V2 {@link StatoTracciatoPendenza}, replicando la stessa
 * combinazione usata da V1 in {@code TracciatiConverter} (output) e
 * {@code ListaTracciatiDTO} (filtro).
 */
public final class TracciatoStatoMapper {

    private static final Logger log = LoggerFactory.getLogger(TracciatoStatoMapper.class);

    public static final String STATO_ELABORAZIONE = "ELABORAZIONE";
    public static final String STATO_COMPLETATO = "COMPLETATO";
    public static final String STATO_SCARTATO = "SCARTATO";
    public static final String STATO_IN_STAMPA = "IN_STAMPA";

    /** I quattro valori che il core scrive in {@code tracciati.stato}. */
    private static final List<String> STATI_DB_NOTI =
            List.of(STATO_ELABORAZIONE, STATO_COMPLETATO, STATO_SCARTATO, STATO_IN_STAMPA);

    private static final String STEP_NUOVO = "NUOVO";
    private static final String STEP_IN_CARICAMENTO = "IN_CARICAMENTO";
    private static final String STEP_CARICAMENTO_OK = "CARICAMENTO_OK";
    private static final String STEP_CARICAMENTO_KO = "CARICAMENTO_KO";

    private TracciatoStatoMapper() {
    }

    public static StatoTracciatoPendenza toRest(String statoDb, String stepElaborazione) {
        return switch (statoDb) {
            case STATO_COMPLETATO -> STEP_CARICAMENTO_OK.equals(stepElaborazione)
                    ? StatoTracciatoPendenza.ESEGUITO
                    : StatoTracciatoPendenza.ESEGUITO_CON_ERRORI;
            case STATO_ELABORAZIONE -> STEP_NUOVO.equals(stepElaborazione)
                    ? StatoTracciatoPendenza.IN_ATTESA
                    : StatoTracciatoPendenza.IN_ELABORAZIONE;
            case STATO_SCARTATO -> StatoTracciatoPendenza.SCARTATO;
            case STATO_IN_STAMPA -> StatoTracciatoPendenza.ELABORAZIONE_STAMPA;
            default -> nonRiconosciuto(statoDb);
        };
    }

    /**
     * Uno stato fuori vocabolario non fa fallire la lettura: la riga resta
     * nell'elenco con {@link StatoTracciatoPendenza#NON_RICONOSCIUTO} e
     * l'anomalia finisce nei log. Sollevare qui un'eccezione faceva fallire
     * l'intera pagina, perche' la mappatura gira dentro uno stream sui
     * risultati: una sola riga anomala rendeva illeggibili anche tutte le
     * altre.
     */
    private static StatoTracciatoPendenza nonRiconosciuto(String statoDb) {
        log.warn("Stato tracciato fuori vocabolario: '{}'. Attesi {}. La riga viene esposta come NON_RICONOSCIUTO.",
                statoDb, STATI_DB_NOTI);
        return StatoTracciatoPendenza.NON_RICONOSCIUTO;
    }

    /** I valori di {@code tracciati.stato} che la mappatura conosce. */
    public static List<String> statiDbNoti() {
        return STATI_DB_NOTI;
    }

    /**
     * Valore della colonna {@code tracciati.stato} corrispondente allo stato
     * REST richiesto (filtro {@code ?stato=}). {@code null} per
     * {@code NON_RICONOSCIUTO}, che per definizione non corrisponde a un
     * valore ma al complemento dei quattro noti: quel caso lo gestisce
     * {@code TracciatoSpecifications.statoExact} con una condizione di
     * esclusione.
     */
    public static String statoDbFor(StatoTracciatoPendenza stato) {
        return switch (stato) {
            case IN_ATTESA, IN_ELABORAZIONE -> STATO_ELABORAZIONE;
            case ESEGUITO, ESEGUITO_CON_ERRORI -> STATO_COMPLETATO;
            case SCARTATO -> STATO_SCARTATO;
            case ELABORAZIONE_STAMPA -> STATO_IN_STAMPA;
            case NON_RICONOSCIUTO -> null;
        };
    }

    /**
     * Pattern {@code LIKE} su {@code bean_dati} (colonna TEXT, nessun supporto
     * JSON nativo portabile tra i 5 dialetti) per distinguere stati REST che
     * condividono lo stesso valore di {@code tracciati.stato} — stessa tecnica
     * di V1 ({@code TracciatoFilter.getDettaglioStato}, match su
     * {@code LikeMode.ANYWHERE}), qui piu' precisa perche' include la chiave
     * JSON oltre al valore. {@code null} se lo stato REST non e' ambiguo
     * (SCARTATO/ELABORAZIONE_STAMPA: un solo stato DB possibile, nessun filtro
     * aggiuntivo necessario).
     */
    public static String beanDatiLikePattern(StatoTracciatoPendenza stato) {
        String step = switch (stato) {
            case IN_ATTESA -> STEP_NUOVO;
            case IN_ELABORAZIONE -> STEP_IN_CARICAMENTO;
            case ESEGUITO -> STEP_CARICAMENTO_OK;
            case ESEGUITO_CON_ERRORI -> STEP_CARICAMENTO_KO;
            case SCARTATO, ELABORAZIONE_STAMPA, NON_RICONOSCIUTO -> null;
        };
        return step == null ? null : "%\"stepElaborazione\":\"" + step + "\"%";
    }
}
