package it.govpay.console.tracciato;

import it.govpay.console.model.StatoOperazionePendenza;

/**
 * Fonte unica della traduzione fra {@code operazioni.stato} (V1, valori
 * grezzi dell'enum ORM {@code StatoOperazioneType}: {@code NON_VALIDO},
 * {@code ESEGUITO_KO}, {@code ESEGUITO_OK}) e {@link StatoOperazionePendenza}
 * (V2, esposto come {@code NON_VALIDO}/{@code SCARTATO}/{@code ESEGUITO}).
 * V1 non espone mai il valore grezzo del DB: lo traduce sempre a un nome
 * semantico diverso (vedi il converter V1 dei tracciati). Usata sia da
 * {@link OperazionePendenzaMapper} (lettura) sia da
 * {@link OperazioneSpecifications} (filtro {@code ?stato=}), cosi' le due
 * derivazioni non possono divergere silenziosamente.
 */
final class StatoOperazioneMapping {

    private StatoOperazioneMapping() {
    }

    static StatoOperazionePendenza map(String raw) {
        return switch (raw) {
            case "ESEGUITO_OK" -> StatoOperazionePendenza.ESEGUITO;
            case "ESEGUITO_KO" -> StatoOperazionePendenza.SCARTATO;
            case "NON_VALIDO" -> StatoOperazionePendenza.NON_VALIDO;
            default -> throw new IllegalArgumentException("Valore stato operazione non riconosciuto: " + raw);
        };
    }

    static String toRaw(StatoOperazionePendenza value) {
        return switch (value) {
            case ESEGUITO -> "ESEGUITO_OK";
            case SCARTATO -> "ESEGUITO_KO";
            case NON_VALIDO -> "NON_VALIDO";
        };
    }
}
