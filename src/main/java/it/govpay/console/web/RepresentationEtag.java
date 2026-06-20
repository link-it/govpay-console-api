package it.govpay.console.web;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import tools.jackson.databind.ObjectMapper;

/**
 * ETag (strong validator) calcolato sulla **rappresentazione** restituita al
 * client, cioe' sul DTO serializzato. Essendo funzione esatta del body della GET,
 * l'ETag cambia se e solo se cambia la rappresentazione: niente campi interni
 * (password, id, riferimenti non esposti) che lo farebbero variare senza che il
 * body cambi, e nessun campo del body (es. liste associate) lasciato fuori dal
 * calcolo.
 */
public final class RepresentationEtag {

    private RepresentationEtag() {
    }

    /** ETag senza virgolette (Spring le aggiunge in {@code ResponseEntity.eTag}). */
    public static String of(Object representation, ObjectMapper objectMapper) {
        return sha256Hex(objectMapper.writeValueAsBytes(representation)).substring(0, 32);
    }

    /**
     * Vero se l'header {@code If-Match} corrisponde all'ETag della rappresentazione
     * corrente. {@code If-Match} usa la comparison <b>forte</b> (RFC 7232): accetta
     * {@code *} e i validatori forti quotati, ma <b>non</b> i weak ETag {@code W/...}
     * (che non matchano mai), coerentemente col fatto che emettiamo strong validator.
     */
    public static boolean matches(String ifMatch, Object representation, ObjectMapper objectMapper) {
        if (ifMatch == null) {
            return false;
        }
        String current = of(representation, objectMapper);
        for (String token : ifMatch.split(",")) {
            String t = token.trim();
            if ("*".equals(t)) {
                return true;
            }
            if (t.startsWith("W/")) {
                // weak validator: mai match sotto la comparison forte di If-Match.
                continue;
            }
            if (current.equals(stripQuotes(t))) {
                return true;
            }
        }
        return false;
    }

    private static String stripQuotes(String t) {
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
