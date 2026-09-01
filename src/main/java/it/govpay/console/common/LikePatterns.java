package it.govpay.console.common;

/**
 * Escape dei caratteri wildcard SQL ({@code %}, {@code _}) e del carattere di
 * escape stesso ({@code \}) per i filtri a match parziale (contains/partial)
 * costruiti come {@code LIKE '%...%'}. Senza escape, un termine di ricerca
 * fornito dall'utente che contiene {@code %} o {@code _} viene interpretato
 * come wildcard SQL invece che come carattere letterale: un termine come
 * {@code ___} (breve ma valido) puo' matchare l'intero contenuto della
 * colonna invece di cercare letteralmente tre underscore.
 *
 * <p>Uso: {@code cb.like(cb.lower(path), "%" + LikePatterns.escape(value.toLowerCase()) + "%", LikePatterns.ESCAPE_CHAR)}.
 */
public final class LikePatterns {

    public static final char ESCAPE_CHAR = '\\';

    private LikePatterns() {
    }

    /** Escape di {@code \}, {@code %} e {@code _} per un uso letterale in {@code LIKE ... ESCAPE '\'}. */
    public static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
