package it.govpay.console.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decodifica la causale di un versamento dal formato persistito nella colonna
 * {@code causale_versamento}. GovPay non memorizza il testo in chiaro ma una
 * stringa codificata {@code "<tipo> <base64>[ <base64>...]"}:
 * <ul>
 *   <li>{@code 01 <b64>} — causale semplice;</li>
 *   <li>{@code 02 <b64> <b64> ...} — causale a spezzoni;</li>
 *   <li>{@code 03 <b64> <b64importo> ...} — spezzoni con importo.</li>
 * </ul>
 *
 * <p>{@link #decodeSimple(String)} replica la {@code getSimple()} di V1 (la forma
 * sintetica che V1 espone all'API): per gli spezzoni ritorna il primo, per gli
 * spezzoni strutturati {@code "<importo>: <spezzone>"}. Valori non riconosciuti
 * (dati legacy/in chiaro) o codifiche malformate vengono restituiti verbatim,
 * senza sollevare eccezioni.
 */
public final class CausaleVersamentoDecoder {

    private CausaleVersamentoDecoder() {
    }

    public static String decodeSimple(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        String[] parts = encoded.trim().split(" ");
        try {
            return switch (parts[0]) {
                case "01", "02" -> parts.length > 1 ? decode(parts[1]) : null;
                case "03" -> decodeStrutturato(parts);
                default -> encoded;
            };
        } catch (RuntimeException e) {
            // base64 o numero malformato: meglio la stringa grezza di un errore.
            return encoded;
        }
    }

    private static String decodeStrutturato(String[] parts) {
        if (parts.length <= 1) {
            return null;
        }
        String spezzone = decode(parts[1]);
        if (parts.length <= 2) {
            return spezzone;
        }
        double importo = Double.parseDouble(decode(parts[2]));
        return importo + ": " + spezzone;
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }
}
