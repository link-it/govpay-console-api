package it.govpay.console.dominio;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Incapsula il formato con cui il logo del dominio e' persistito nella colonna
 * {@code domini.logo}: non i byte grezzi dell'immagine, ma i byte UTF-8 del suo
 * testo Base64. E' l'unico punto che conosce questa codifica, cosi' che i punti
 * di lettura/scrittura lavorino sempre con i byte grezzi dell'immagine.
 */
public final class DominioLogoCodec {

    private DominioLogoCodec() {
    }

    /**
     * Decodifica il contenuto memorizzato in colonna (testo Base64 dell'immagine,
     * in byte UTF-8) nei byte grezzi dell'immagine. Tollera un eventuale prefisso
     * data-URI ({@code data:...;base64,}).
     */
    public static byte[] decode(byte[] stored) {
        if (stored == null || stored.length == 0) {
            return stored;
        }
        return Base64.getDecoder().decode(base64Of(stored));
    }

    /**
     * Codifica i byte grezzi dell'immagine nel formato memorizzato in colonna
     * (testo Base64 nudo, in byte UTF-8).
     */
    public static byte[] encode(byte[] rawImage) {
        if (rawImage == null || rawImage.length == 0) {
            return rawImage;
        }
        return Base64.getEncoder().encodeToString(rawImage).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Costruisce il data-URI per il payload delle stampe riusando la stringa
     * Base64 gia' presente in colonna e ricavando il content-type dai magic bytes
     * dell'immagine decodificata.
     */
    public static String toDataUri(byte[] stored) {
        if (stored == null || stored.length == 0) {
            return "";
        }
        String contentType = LogoMimeDetector.detect(decode(stored));
        if (contentType == null) {
            contentType = LogoMimeDetector.IMAGE_PNG;
        }
        return "data:" + contentType + ";base64," + base64Of(stored);
    }

    private static String base64Of(byte[] stored) {
        String value = new String(stored, StandardCharsets.UTF_8).trim();
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma >= 0) {
            return value.substring(comma + 1);
        }
        return value;
    }
}
