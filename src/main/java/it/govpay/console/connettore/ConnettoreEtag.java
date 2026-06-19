package it.govpay.console.connettore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * ETag (strong validator) content-based di un connettore, calcolato sulle sole
 * proprieta' di **configurazione** (le credenziali non fanno parte della
 * rappresentazione). Deterministico sull'ordine delle chiavi.
 */
public final class ConnettoreEtag {

    private ConnettoreEtag() {
    }

    public static String compute(Map<String, String> properties) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String key : ConnettoreProprietaKeys.CONFIG_KEYS) {
            String value = properties.get(key);
            if (value != null) {
                sorted.put(key, value);
            }
        }
        StringBuilder sb = new StringBuilder();
        sorted.forEach((k, v) -> sb.append(k).append('=').append(v).append('|'));
        return sha256Hex(sb.toString()).substring(0, 32);
    }

    public static boolean matches(String ifMatch, Map<String, String> properties) {
        if (ifMatch == null) {
            return false;
        }
        String current = compute(properties);
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

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
