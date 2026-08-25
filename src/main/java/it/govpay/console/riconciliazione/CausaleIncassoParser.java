package it.govpay.console.riconciliazione;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port di {@code it.govpay.core.utils.IncassoUtils} (V1, non sul classpath di
 * console-api): estrae dalla causale del bonifico SCT il riferimento alla
 * riconciliazione, secondo le specifiche AgID SACIV 1.2.1. Pattern e logica
 * identici a V1, self-contained (nessuna dipendenza da govpay-core).
 */
public final class CausaleIncassoParser {

    private static final Pattern PATTERN_SINGOLO = Pattern.compile("RF[SB].([0-9A-Za-z\\-_]+)");
    private static final Pattern PATTERN_CUMULATIVO =
            Pattern.compile("PUR.LGPE-RIVERSAMENTO.(TXT.[0-9]{1}.)?URI.([0-9A-Za-z\\-_]+)");
    private static final Pattern PATTERN_IDF = Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d[0-9A-Za-z_]*-\\S*");
    private static final Pattern PATTERN_IUV = Pattern.compile("[0-9]{15,17}|^RF.*");

    private CausaleIncassoParser() {
    }

    /**
     * Riferimento singolo (IUV, {@code RFS}/{@code RFB}): in V2 usato solo per
     * riconoscere e rifiutare esplicitamente questa modalità, non più
     * supportata in scrittura.
     */
    public static String getRiferimentoIncassoSingolo(String causale) {
        Matcher matcher = PATTERN_SINGOLO.matcher(causale);
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String match = matcher.group(i);
                if (match != null && PATTERN_IUV.matcher(match).find()) {
                    return match;
                }
            }
        }
        return null;
    }

    /** Riferimento cumulativo (idFlusso di rendicontazione), l'unica modalità supportata in scrittura in V2. */
    public static String getRiferimentoIncassoCumulativo(String causale) {
        Matcher matcher = PATTERN_CUMULATIVO.matcher(causale);
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String match = matcher.group(i);
                if (match != null && PATTERN_IDF.matcher(match).find()) {
                    return match;
                }
            }
        }
        return null;
    }
}
