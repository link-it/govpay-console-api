package it.govpay.console.intermediario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import it.govpay.console.entity.Intermediario;

/**
 * Calcolo dell'ETag (strong validator) di un intermediario a partire dalla
 * rappresentazione corrente. L'entity non ha una colonna di versione: l'ETag e'
 * un hash content-based dei campi rilevanti, ricalcolato a ogni lettura/scrittura.
 * Essendo derivato dall'intera rappresentazione e' un validatore forte, adatto a
 * {@code If-Match} per la concorrenza ottimistica.
 */
public final class IntermediarioEtag {

    private IntermediarioEtag() {
    }

    /** ETag senza virgolette (Spring le aggiunge in {@code ResponseEntity.eTag}). */
    public static String compute(Intermediario entity) {
        String canonical = String.join("|",
                str(entity.getId()),
                str(entity.getCodIntermediario()),
                str(entity.getDenominazione()),
                str(entity.getPrincipal()),
                str(entity.getCodConnettorePdd()),
                str(entity.getAbilitato()));
        return sha256Hex(canonical).substring(0, 32);
    }

    /**
     * Vero se l'header {@code If-Match} corrisponde all'ETag corrente. Accetta
     * {@code *} (qualsiasi rappresentazione esistente), valori quotati e il
     * prefisso debole {@code W/} (confronto sull'opaque-tag).
     */
    public static boolean matches(String ifMatch, Intermediario entity) {
        if (ifMatch == null) {
            return false;
        }
        String current = compute(entity);
        for (String token : ifMatch.split(",")) {
            String t = normalize(token);
            if ("*".equals(t) || current.equals(t)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String raw) {
        String t = raw.trim();
        if (t.startsWith("W/")) {
            t = t.substring(2).trim();
        }
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
