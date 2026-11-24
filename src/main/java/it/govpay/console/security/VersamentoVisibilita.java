package it.govpay.console.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Regola di visibilità ACL di una pendenza per l'operatore corrente,
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
     * Predicato query-side per una pendenza del modello v3, dai path dei suoi assi (stessa
     * regola di {@link #isVisibile(Long, Long, Long, Supplier, OperatoreCorrente)}): la
     * unita' operativa sta sulla posizione (per una pendenza senza opzione, e' quella
     * storica) e puo' mancare, e una pendenza senza unita' operativa vale come quella
     * {@code EC} del suo dominio.
     *
     * @param dominiConUoEcVisibile domini di cui l'operatore ha la unita' operativa {@code EC}
     */
    public static Predicate predicate(CriteriaBuilder cb, Path<Long> idDominio, Expression<Long> idUo,
                                      Path<Long> idTipoVersamento, Set<Long> dominiConUoEcVisibile,
                                      OperatoreCorrente operatore) {
        Predicate p = cb.conjunction();

        if (!operatore.tuttiIDomini()) {
            Set<Long> dominiInteri = operatore.idDominiInteri();
            Set<Long> uoVisibili = operatore.idUoVisibili();
            List<Predicate> alternative = new ArrayList<>();
            if (!isEmpty(dominiInteri)) {
                alternative.add(idDominio.in(dominiInteri));
            }
            if (!isEmpty(uoVisibili)) {
                alternative.add(idUo.in(uoVisibili));
                if (!isEmpty(dominiConUoEcVisibile)) {
                    alternative.add(cb.and(cb.isNull(idUo), idDominio.in(dominiConUoEcVisibile)));
                }
            }
            if (alternative.isEmpty()) {
                return cb.disjunction();
            }
            p = cb.and(p, cb.or(alternative.toArray(new Predicate[0])));
        }

        if (!operatore.tuttiITipiVersamento()) {
            Set<Long> tipi = operatore.idTipiVersamentoVisibili();
            if (isEmpty(tipi)) {
                return cb.disjunction();
            }
            p = cb.and(p, idTipoVersamento.in(tipi));
        }
        return p;
    }

    /**
     * Check post-fetch su una pendenza del modello v3, identificata dalle chiavi dei
     * suoi assi: l'unita' operativa sta sulla posizione debitoria e puo' mancare.
     * Una pendenza senza unita' operativa equivale a quella {@code EC} del suo dominio:
     * e' visibile a chi ha quella unita' operativa.
     *
     * @param idUo                       unita' operativa della posizione, o {@code null}
     * @param idTipoVersamento           tipo versamento globale (non il tipo del dominio)
     * @param dominiConUoEcVisibile      domini di cui l'operatore ha la unita' operativa
     *                                   {@code EC}; letto solo se serve
     */
    public static boolean isVisibile(Long idDominio, Long idUo, Long idTipoVersamento,
                                     Supplier<Set<Long>> dominiConUoEcVisibile, OperatoreCorrente operatore) {
        if (!operatore.tuttiIDomini()) {
            if (idDominio == null) {
                return false;
            }
            boolean visibile = operatore.idDominiInteri().contains(idDominio)
                    || (idUo != null && operatore.idUoVisibili().contains(idUo))
                    || (idUo == null && !isEmpty(operatore.idUoVisibili())
                            && dominiConUoEcVisibile.get().contains(idDominio));
            if (!visibile) {
                return false;
            }
        }
        if (!operatore.tuttiITipiVersamento()) {
            return idTipoVersamento != null
                    && operatore.idTipiVersamentoVisibili().contains(idTipoVersamento);
        }
        return true;
    }

    private static boolean isEmpty(Set<Long> set) {
        return set == null || set.isEmpty();
    }
}
