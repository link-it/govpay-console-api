package it.govpay.console.pendenza;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Codifica/decodifica del cursore opaque per la paginazione keyset di
 * {@code GET /pendenze} (scope G issue #9).
 *
 * <p>Formato: base64 URL-safe (no padding) di {@code "<dataOraUltimoAggiornamento ISO_8601>|<id>"}.
 * Esempio: {@code "2026-06-12T10:15:30.123Z|42"} → base64.
 *
 * <p><b>Non firmato</b> (allineato issue): il cursore e' un hint di
 * paginazione, non un token di sicurezza. La query e' comunque scoped sulla
 * visibilita' ACL dell'operatore corrente, quindi una manomissione non
 * permette accessi non autorizzati — al piu' fa saltare ordinamento/pagine.
 */
public final class CursorCodec {

    private static final String SEPARATOR = "|";

    private CursorCodec() {}

    public static String encode(OffsetDateTime dataOraUltimoAggiornamento, long id) {
        String raw = dataOraUltimoAggiornamento.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new BadCursorException("Cursor vuoto: rispedire il valore ricevuto in 'nextCursor'.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new BadCursorException("Cursor malformato: codifica base64 non valida.", e);
        }
        String raw = new String(decoded, StandardCharsets.UTF_8);
        int sep = raw.lastIndexOf(SEPARATOR);
        if (sep <= 0 || sep == raw.length() - 1) {
            throw new BadCursorException("Cursor malformato: formato '<timestamp>|<id>' atteso.");
        }
        String tsPart = raw.substring(0, sep);
        String idPart = raw.substring(sep + 1);
        OffsetDateTime ts;
        try {
            ts = OffsetDateTime.parse(tsPart);
        } catch (DateTimeParseException e) {
            throw new BadCursorException("Cursor malformato: timestamp non parseabile.", e);
        }
        long id;
        try {
            id = Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            throw new BadCursorException("Cursor malformato: id non numerico.", e);
        }
        return new Cursor(ts, id);
    }

    public record Cursor(OffsetDateTime dataOraUltimoAggiornamento, long id) {}
}
