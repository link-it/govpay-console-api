package it.govpay.console.riconciliazione;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.LikePatterns;
import it.govpay.console.entity.Incasso;
import it.govpay.console.model.StatoRiconciliazione;

/**
 * Predicati di ricerca per la collection {@code GET /riconciliazioni}
 * (entità {@link Incasso}). A differenza di {@code FrSpecifications}, il
 * filtro ACL non lavora su una FK numerica (la tabella {@code incassi} non ne
 * ha una verso {@code domini}): {@link #visibiliPerOperatore} riceve i codici
 * dominio già risolti dal chiamante (via
 * {@link it.govpay.console.eventi.EventoAcl#codiciVisibili}).
 */
public final class IncassoSpecifications {

    private IncassoSpecifications() {
    }

    public static Specification<Incasso> idDominioExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("codDominio"), value);
    }

    /** Limite inferiore incluso sulla data di registrazione della riconciliazione. */
    public static Specification<Incasso> dataDa(OffsetDateTime da) {
        if (da == null) {
            return null;
        }
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("dataOraIncasso"), da);
    }

    /** Limite superiore incluso sulla data di registrazione della riconciliazione. */
    public static Specification<Incasso> dataA(OffsetDateTime a) {
        if (a == null) {
            return null;
        }
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("dataOraIncasso"), a);
    }

    /** Traduce lo stato API sul valore raw a DB ({@code incassi.stato}: NUOVO/ACQUISITO/ERRORE). */
    public static Specification<Incasso> statoEsatto(StatoRiconciliazione stato) {
        if (stato == null) {
            return null;
        }
        String statoRaw = switch (stato) {
            case IN_ELABORAZIONE -> "NUOVO";
            case ACQUISITA -> "ACQUISITO";
            case ERRORE -> "ERRORE";
        };
        return (root, q, cb) -> cb.equal(root.get("stato"), statoRaw);
    }

    /** Match parziale su {@code sct}, come V1. */
    public static Specification<Incasso> sctParziale(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.like(root.get("sct"), "%" + LikePatterns.escape(value) + "%", LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<Incasso> idFlussoExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("codFlussoRendicontazione"), value);
    }

    /** Filtro legacy: solo le riconciliazioni singole registrate dalle versioni precedenti. */
    public static Specification<Incasso> iuvExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("iuv"), value);
    }

    /**
     * ACL: {@code tuttiIDomini} → nessun vincolo; nessun codice visibile →
     * predicato sempre falso (risultato vuoto, mai 403); altrimenti
     * {@code cod_dominio IN (codiciVisibili)}. Stessa semantica a 3 rami di
     * {@code DominioVisibilita.predicate}, qui su stringa anziché FK numerica.
     */
    public static Specification<Incasso> visibiliPerOperatore(boolean tuttiIDomini, List<String> codiciVisibili) {
        if (tuttiIDomini) {
            return null;
        }
        if (codiciVisibili == null || codiciVisibili.isEmpty()) {
            return (root, q, cb) -> cb.disjunction();
        }
        return (root, q, cb) -> root.get("codDominio").in(codiciVisibili);
    }
}
