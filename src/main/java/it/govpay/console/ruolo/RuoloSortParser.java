package it.govpay.console.ruolo;

import java.util.Comparator;

/**
 * Parser di `?sort=` per `GET /ruoli`. L'unico campo ordinabile e' `idRuolo`
 * (prefisso `-` per DESC, `+`/nessuno per ASC). La lista dei ruoli e' un
 * catalogo derivato (distinct su {@code acl.ruolo}) e viene ordinata in memoria,
 * quindi il parser restituisce direttamente un {@link Comparator}. Campi non
 * riconosciuti causano {@link IllegalArgumentException} (mappata a 400).
 */
public final class RuoloSortParser {

    private static final String FIELD_ID_RUOLO = "idRuolo";

    private RuoloSortParser() {
    }

    public static Comparator<String> parse(String raw) {
        String value = (raw == null || raw.isBlank()) ? FIELD_ID_RUOLO : raw.trim();
        boolean descending = false;
        String field = value;
        if (field.startsWith("-")) {
            descending = true;
            field = field.substring(1);
        } else if (field.startsWith("+")) {
            field = field.substring(1);
        }
        if (!FIELD_ID_RUOLO.equals(field)) {
            throw new IllegalArgumentException("Campo di sort non supportato: " + field
                    + ". Campi ammessi: [" + FIELD_ID_RUOLO + "]");
        }
        Comparator<String> comparator = Comparator.comparing(String::toLowerCase);
        return descending ? comparator.reversed() : comparator;
    }
}
