package it.govpay.console.security;

import java.util.Set;

import it.govpay.console.entity.Versamento;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Regola di visibilità ACL di un {@link Versamento} per l'operatore corrente,
 * single-source per tutti i domini che leggono le pendenze (pendenze, ricevute,
 * avvisi). Replica i 3 livelli di V1:
 * <ul>
 *   <li><b>dominio/UO</b>: dominio "intero" (visibile per intero) OR UO specifica
 *       visibile;</li>
 *   <li><b>tipoVersamento</b>: incluso solo se nei tipi autorizzati.</li>
 * </ul>
 * Espone sia il predicato query-side (da spingere nella ricerca paginata, dove il
 * filtro post-fetch romperebbe page size e conteggi) sia il check post-fetch (per
 * i {@code get} di dettaglio con 404 anti-leak).
 *
 * <p>Operatore con {@code tuttiIDomini}/{@code tuttiITipiVersamento} → nessun
 * vincolo sul rispettivo asse. Insieme di domini/tipi autorizzati vuoto →
 * predicato sempre falso (risultato vuoto, mai 403).
 */
public final class VersamentoVisibilita {

    private VersamentoVisibilita() {
    }

    /**
     * Predicato query-side da applicare a una ricerca. {@code versamento} è il path
     * all'entità {@link Versamento}: la root stessa per una query su versamenti, o
     * {@code root.get("versamento")} per una query su un'entità collegata (es.
     * {@code Rpt}).
     */
    public static Predicate predicate(CriteriaBuilder cb, Path<?> versamento, OperatoreCorrente operatore) {
        Predicate p = cb.conjunction();

        if (!operatore.tuttiIDomini()) {
            Set<Long> dominiInteri = operatore.idDominiInteri();
            Set<Long> uoVisibili = operatore.idUoVisibili();
            if (isEmpty(dominiInteri) && isEmpty(uoVisibili)) {
                return cb.disjunction();
            }
            Predicate viaDominio = isEmpty(dominiInteri)
                    ? cb.disjunction()
                    : versamento.get("dominio").get("id").in(dominiInteri);
            Predicate viaUo = isEmpty(uoVisibili)
                    ? cb.disjunction()
                    : versamento.get("unitaOperativa").get("id").in(uoVisibili);
            p = cb.and(p, cb.or(viaDominio, viaUo));
        }

        if (!operatore.tuttiITipiVersamento()) {
            Set<Long> tipi = operatore.idTipiVersamentoVisibili();
            if (isEmpty(tipi)) {
                return cb.disjunction();
            }
            p = cb.and(p, versamento.get("tipoVersamento").get("id").in(tipi));
        }

        return p;
    }

    /** Check post-fetch su una singola entità già caricata. */
    public static boolean isVisibile(Versamento v, OperatoreCorrente operatore) {
        if (!isDominioOrUoVisible(v, operatore)) {
            return false;
        }
        if (!operatore.tuttiITipiVersamento()) {
            if (v.getTipoVersamento() == null) {
                return false;
            }
            return operatore.idTipiVersamentoVisibili().contains(v.getTipoVersamento().getId());
        }
        return true;
    }

    private static boolean isDominioOrUoVisible(Versamento v, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            return true;
        }
        if (v.getDominio() == null) {
            return false;
        }
        if (operatore.idDominiInteri().contains(v.getDominio().getId())) {
            return true;
        }
        return v.getUnitaOperativa() != null
                && operatore.idUoVisibili().contains(v.getUnitaOperativa().getId());
    }

    private static boolean isEmpty(Set<Long> set) {
        return set == null || set.isEmpty();
    }
}
