package it.govpay.console.tracciato;

import java.util.List;

import it.govpay.console.model.TipoOperazionePendenza;

/**
 * Fonte unica della traduzione fra {@code operazioni.tipo_operazione} (V1,
 * valori grezzi dell'enum ORM {@code TipoOperazioneType}: {@code ADD},
 * {@code DEL}, {@code INC}, {@code N_V}) e {@link TipoOperazionePendenza}
 * (V2, solo {@code ADD}/{@code DEL}/{@code NON_VALIDA}). V1 tratta
 * {@code INC} come sinonimo di {@code N_V} (stesso ramo default nello switch
 * del converter V1 dei tracciati): il filtro su {@code NON_VALIDA} deve
 * quindi coprire entrambi i valori grezzi, non un singolo match. Usata sia
 * da {@link OperazionePendenzaMapper} (lettura) sia da
 * {@link OperazioneSpecifications} (filtro {@code ?tipoOperazione=}).
 */
final class TipoOperazioneMapping {

    private TipoOperazioneMapping() {
    }

    static TipoOperazionePendenza map(String raw) {
        return switch (raw) {
            case "ADD" -> TipoOperazionePendenza.ADD;
            case "DEL" -> TipoOperazionePendenza.DEL;
            case "N_V", "INC" -> TipoOperazionePendenza.NON_VALIDA;
            default -> throw new IllegalArgumentException("Valore tipo operazione non riconosciuto: " + raw);
        };
    }

    static List<String> toRaw(TipoOperazionePendenza value) {
        return switch (value) {
            case ADD -> List.of("ADD");
            case DEL -> List.of("DEL");
            case NON_VALIDA -> List.of("N_V", "INC");
        };
    }
}
