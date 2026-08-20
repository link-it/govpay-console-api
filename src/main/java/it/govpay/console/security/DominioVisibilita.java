package it.govpay.console.security;

import java.util.Set;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Regola di visibilità ACL su dominio, per entità legate direttamente a un
 * {@code Dominio} senza passare da {@link it.govpay.console.entity.Versamento}
 * (nessun asse UO/tipoVersamento: es. i flussi di rendicontazione, che pagoPA
 * non attribuisce a una Unità Operativa specifica). Solo i domini "interi"
 * dell'operatore ({@link OperatoreCorrente#idDominiInteri()}) danno visibilità;
 * un dominio su cui l'operatore ha solo UO parziali resta non visibile qui.
 *
 * <p>Operatore con {@code tuttiIDomini} → nessun vincolo. Insieme di domini
 * interi vuoto → predicato sempre falso (risultato vuoto, mai 403).
 */
public final class DominioVisibilita {

    private DominioVisibilita() {
    }

    /**
     * Predicato query-side. {@code idDominio} è il path alla colonna FK tecnica
     * (es. {@code root.get("idDominio")} per un'entità con quel campo).
     */
    public static Predicate predicate(CriteriaBuilder cb, Path<Long> idDominio, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            return cb.conjunction();
        }
        Set<Long> dominiInteri = operatore.idDominiInteri();
        if (dominiInteri == null || dominiInteri.isEmpty()) {
            return cb.disjunction();
        }
        return idDominio.in(dominiInteri);
    }

    /** Check post-fetch su un {@code id_dominio} già caricato (per i {@code get} di dettaglio). */
    public static boolean isVisibile(Long idDominio, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            return true;
        }
        return idDominio != null && operatore.idDominiInteri().contains(idDominio);
    }
}
