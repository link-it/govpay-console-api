package it.govpay.console.rendicontazione;

import java.util.ArrayList;
import java.util.List;

import it.govpay.console.model.AnomaliaRendicontazione;

/**
 * Parsing di {@code rendicontazioni.anomalie}: formato {@code
 * "codice#descrizione"}, piu' anomalie separate da {@code |}. Lo split sul
 * codice usa solo il primo {@code #} di ogni segmento. Un segmento senza
 * {@code #} produce un'anomalia con {@code codice} assente e l'intero
 * segmento come descrizione.
 */
public final class AnomalieRendicontazioneParser {

    private AnomalieRendicontazioneParser() {
    }

    public static List<AnomaliaRendicontazione> parse(String anomalie) {
        List<AnomaliaRendicontazione> risultato = new ArrayList<>();
        if (anomalie == null || anomalie.isBlank()) {
            return risultato;
        }
        for (String segmento : anomalie.split("\\|")) {
            if (segmento.isBlank()) {
                continue;
            }
            risultato.add(parseSegmento(segmento));
        }
        return risultato;
    }

    private static AnomaliaRendicontazione parseSegmento(String segmento) {
        AnomaliaRendicontazione anomalia = new AnomaliaRendicontazione();
        int separatore = segmento.indexOf('#');
        if (separatore < 0) {
            anomalia.setDescrizione(segmento.trim());
        } else {
            anomalia.setCodice(segmento.substring(0, separatore).trim());
            anomalia.setDescrizione(segmento.substring(separatore + 1).trim());
        }
        return anomalia;
    }
}
