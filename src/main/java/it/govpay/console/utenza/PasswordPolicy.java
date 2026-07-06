package it.govpay.console.utenza;

import java.util.ArrayList;
import java.util.List;

import it.govpay.console.web.BadRequestException;

/**
 * Policy delle password delle utenze, allineata alla validazione del core:
 * almeno {@value #MIN_LENGTH} caratteri con una lettera minuscola, una
 * maiuscola e una cifra, nessun carattere di spaziatura, lunghezza massima
 * {@value #MAX_LENGTH}.
 */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 255;

    private PasswordPolicy() {
    }

    /**
     * Valida la password contro la policy; in caso di violazioni lancia una
     * {@link BadRequestException} che le elenca tutte.
     */
    public static void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw new BadRequestException("Il campo 'nuovaPassword' e' obbligatorio.");
        }
        List<String> violazioni = new ArrayList<>();
        if (password.length() < MIN_LENGTH) {
            violazioni.add("almeno " + MIN_LENGTH + " caratteri");
        }
        if (password.length() > MAX_LENGTH) {
            violazioni.add("al massimo " + MAX_LENGTH + " caratteri");
        }
        if (password.chars().noneMatch(c -> c >= 'a' && c <= 'z')) {
            violazioni.add("almeno una lettera minuscola");
        }
        if (password.chars().noneMatch(c -> c >= 'A' && c <= 'Z')) {
            violazioni.add("almeno una lettera maiuscola");
        }
        if (password.chars().noneMatch(c -> c >= '0' && c <= '9')) {
            violazioni.add("almeno una cifra");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            violazioni.add("nessun carattere di spaziatura");
        }
        if (!violazioni.isEmpty()) {
            throw new BadRequestException(
                    "La password non rispetta la policy: richiesti " + String.join(", ", violazioni) + ".");
        }
    }
}
