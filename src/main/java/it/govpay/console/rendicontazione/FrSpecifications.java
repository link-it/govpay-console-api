package it.govpay.console.rendicontazione;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Fr;
import it.govpay.console.entity.Rendicontazione;
import it.govpay.console.model.StatoFlussoRendicontazione;
import it.govpay.console.security.DominioVisibilita;
import it.govpay.console.security.OperatoreCorrente;
import jakarta.persistence.criteria.Subquery;

/**
 * Predicati di ricerca per la collection {@code GET /flussi-rendicontazione}
 * (entità {@link Fr}). Nessuna modifica allo schema esistente: filtri e ACL
 * sono tutti espressi su colonne già presenti in {@code fr}/{@code rendicontazioni}.
 */
public final class FrSpecifications {

    private FrSpecifications() {
    }

    public static Specification<Fr> idDominioExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("codDominio"), value);
    }

    public static Specification<Fr> idFlussoExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("codFlusso"), value);
    }

    public static Specification<Fr> idPspExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("codPsp"), value);
    }

    /** Limite inferiore incluso sulla data di acquisizione del flusso. */
    public static Specification<Fr> dataAcquisizioneDa(OffsetDateTime da) {
        if (da == null) {
            return null;
        }
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("dataAcquisizione"), da);
    }

    /** Limite superiore incluso sulla data di acquisizione del flusso. */
    public static Specification<Fr> dataAcquisizioneA(OffsetDateTime a) {
        if (a == null) {
            return null;
        }
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("dataAcquisizione"), a);
    }

    /**
     * Traduce lo stato "arricchito" dell'API (che include {@code OBSOLETO}) sulle
     * colonne reali {@code stato} (3 valori raw) + {@code obsoleto}. {@code OBSOLETO}
     * prevale: una riga con {@code obsoleto=true} è sempre e solo {@code OBSOLETO}
     * indipendentemente dal suo stato raw.
     */
    public static Specification<Fr> statoEsatto(StatoFlussoRendicontazione stato) {
        if (stato == null) {
            return null;
        }
        if (stato == StatoFlussoRendicontazione.OBSOLETO) {
            return (root, q, cb) -> cb.isTrue(root.get("obsoleto"));
        }
        String statoRaw = switch (stato) {
            case ACQUISITO -> "ACCETTATA";
            case ANOMALO -> "ANOMALA";
            case RIFIUTATO -> "RIFIUTATA";
            case OBSOLETO -> throw new IllegalStateException("gestito sopra");
        };
        return (root, q, cb) -> cb.and(
                cb.isFalse(root.get("obsoleto")),
                cb.equal(root.get("stato"), statoRaw));
    }

    /** {@code incassato=true} → {@code id_incasso IS NOT NULL}; {@code false} → {@code IS NULL}. */
    public static Specification<Fr> incassato(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> value
                ? cb.isNotNull(root.get("idIncasso"))
                : cb.isNull(root.get("idIncasso"));
    }

    /** Solo i flussi che contengono quello IUV (join a {@code rendicontazioni}). */
    public static Specification<Fr> iuvExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> {
            Subquery<Long> sub = q.subquery(Long.class);
            var rnd = sub.from(Rendicontazione.class);
            sub.select(rnd.get("idFr"));
            sub.where(cb.equal(rnd.get("idFr"), root.get("id")), cb.equal(rnd.get("iuv"), value));
            return cb.exists(sub);
        };
    }

    public static Specification<Fr> visibiliPerOperatore(OperatoreCorrente operatore) {
        return (root, q, cb) -> DominioVisibilita.predicate(cb, root.get("idDominio"), operatore);
    }
}
