package it.govpay.console.web;

import java.util.Collections;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Validazioni comuni dei parametri di query delle collection paginate (pendenze,
 * ricevute, ...): rifiuto dei filtri non supportati in Fase 1 e regole di mutua
 * esclusione della modalità cursor. I messaggi dei {@link BadRequestException}
 * sono parlanti per orientare il client (→ 400 problem+json).
 */
public final class ListQueryValidator {

    private ListQueryValidator() {
    }

    /** Rifiuta ogni parametro di query non incluso in {@code allowed}. */
    public static void rejectUnsupported(HttpServletRequest request, Set<String> allowed) {
        if (request == null) {
            return;
        }
        for (String name : Collections.list(request.getParameterNames())) {
            if (!allowed.contains(name)) {
                throw new BadRequestException("Filtro non supportato in Fase 1: " + name);
            }
        }
    }

    /**
     * Modalità cursor attiva se {@code ?cursor} è presente nella query string,
     * anche con valore vuoto ("prima pagina cursor-mode").
     */
    public static boolean isCursorMode(HttpServletRequest request) {
        return request != null && request.getParameterMap().containsKey("cursor");
    }

    /** {@code true} se il parametro è presente con almeno un valore non vuoto. */
    public static boolean isExplicit(HttpServletRequest request, String name) {
        if (request == null) {
            return false;
        }
        String[] values = request.getParameterMap().get(name);
        if (values == null) {
            return false;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** {@code true} solo se il parametro è presente con valore {@code true}. */
    public static boolean isExplicitTrue(HttpServletRequest request, String name) {
        if (request == null) {
            return false;
        }
        String[] values = request.getParameterMap().get(name);
        if (values == null) {
            return false;
        }
        for (String v : values) {
            if (v != null && "true".equalsIgnoreCase(v.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * In modalità cursor sono incompatibili {@code page}/{@code sort} espliciti e
     * {@code total=true}. {@code total=false} (default) è ammesso: solo la richiesta
     * esplicita del conteggio confligge con il keyset. {@code fixedSort} descrive
     * l'ordinamento fisso della risorsa (es. {@code "dataPagamento DESC, id DESC"}).
     */
    public static void rejectCursorIncompatible(HttpServletRequest request, String fixedSort) {
        if (request == null) {
            return;
        }
        if (isExplicit(request, "page")) {
            throw new BadRequestException(
                    "Parametri 'page' e 'cursor' mutuamente esclusivi: usa solo uno dei due "
                            + "(cursor per paginazione keyset, page per paginazione offset).");
        }
        if (isExplicit(request, "sort")) {
            throw new BadRequestException(
                    "In modalita' cursor (?cursor=...) l'ordinamento e' fisso (" + fixedSort
                            + "): non specificare ?sort=.");
        }
        if (isExplicitTrue(request, "total")) {
            throw new BadRequestException(
                    "In modalita' cursor (?cursor=...) il conteggio totale non e' disponibile; "
                            + "?total=true non e' compatibile. Usa la presenza di 'nextCursor' "
                            + "in risposta per sapere se ci sono altre pagine.");
        }
    }
}
