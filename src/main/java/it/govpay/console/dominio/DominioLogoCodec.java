package it.govpay.console.dominio;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

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
     * Logo in archivio utilizzabile: i byte dell'immagine e il tipo riconosciuto.
     */
    public record LogoArchiviato(byte[] immagine, String contentType) {

        @Override
        public boolean equals(Object o) {
            return o instanceof LogoArchiviato(byte[] altraImmagine, String altroContentType)
                    && Arrays.equals(immagine, altraImmagine)
                    && Objects.equals(contentType, altroContentType);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(immagine) + Objects.hashCode(contentType);
        }

        @Override
        public String toString() {
            return "LogoArchiviato[contentType=" + contentType + ", byte="
                    + (immagine == null ? 0 : immagine.length) + "]";
        }
    }

    /**
     * Lettura tollerante dei loghi gia' in archivio (issue #124): restituisce
     * vuoto, invece di un errore, se il valore non e' Base64 o se i byte
     * decodificati non sono un'immagine riconosciuta. Gli a capo dei valori
     * legacy sono ignorati. Un valore come {@code /domini/{id}/logo}, salvato
     * dalle vecchie PUT dell'API Backoffice, e' Base64 formalmente valido ma
     * non decodifica un'immagine: per questo serve il controllo del tipo.
     */
    public static Optional<LogoArchiviato> leggi(byte[] stored) {
        if (stored == null || stored.length == 0) {
            return Optional.empty();
        }
        byte[] immagine;
        try {
            immagine = Base64.getDecoder().decode(base64Of(stored).replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String contentType = LogoMimeDetector.detect(immagine);
        if (contentType == null || !contentType.startsWith("image/")) {
            return Optional.empty();
        }
        return Optional.of(new LogoArchiviato(immagine, contentType));
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
     * Costruisce il data-URI per il payload delle stampe con il content-type
     * riconosciuto, SVG compreso. Restituisce {@code ""} se il logo in archivio
     * non e' utilizzabile (vedi {@link #leggi}): la stampa usa il logo di default.
     */
    public static String toDataUri(byte[] stored) {
        return leggi(stored)
                .map(logo -> "data:" + logo.contentType() + ";base64,"
                        + Base64.getEncoder().encodeToString(logo.immagine()))
                .orElse("");
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
