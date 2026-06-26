package it.govpay.console.pagination;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Codifica/decodifica del cursore opaque per la paginazione keyset, condiviso da
 * tutte le collection cursor-paginated (pendenze, ricevute, ...).
 *
 * <p>Formato: base64 URL-safe (no padding) di {@code "<timestamp ISO_8601>|<id>"}.
 * Esempio: {@code "2026-06-12T10:15:30.123Z|42"} → base64. Il significato del
 * timestamp dipende dall'ordinamento della collection (es.
 * {@code dataOraUltimoAggiornamento} per le pendenze, {@code dataPagamento} per le
 * ricevute).
 *
 * <p><b>Non firmato</b>: il cursore è un hint di paginazione, non un token di
 * sicurezza. La query è comunque scoped sulla visibilità ACL dell'operatore
 * corrente, quindi una manomissione non permette accessi non autorizzati — al più
 * fa saltare ordinamento/pagine.
 */
public final class CursorCodec {

    private static final String SEPARATOR = "|";

    private CursorCodec() {}

    public static String encode(OffsetDateTime timestamp, long id) {
        String raw = timestamp.toString() + SEPARATOR + id;
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

    public record Cursor(OffsetDateTime timestamp, long id) {}
}
