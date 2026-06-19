package it.govpay.console.stazione;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import it.govpay.console.entity.Stazione;

/**
 * ETag (strong validator) content-based di una stazione. L'entity non ha una
 * colonna di versione di concorrenza: l'ETag e' un hash dei campi rilevanti,
 * ricalcolato a ogni lettura/scrittura.
 */
public final class StazioneEtag {

    private StazioneEtag() {
    }

    public static String compute(Stazione entity) {
        String canonical = String.join("|",
                str(entity.getId()),
                str(entity.getCodStazione()),
                str(entity.getPassword()),
                str(entity.getAbilitato()),
                str(entity.getApplicationCode()),
                str(entity.getVersione()),
                str(entity.getIntermediario() == null ? null : entity.getIntermediario().getId()));
        return sha256Hex(canonical).substring(0, 32);
    }

    public static boolean matches(String ifMatch, Stazione entity) {
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
