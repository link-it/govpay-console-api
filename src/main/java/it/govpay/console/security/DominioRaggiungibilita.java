package it.govpay.console.security;

import java.util.Set;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Regola di visibilita' ACL su dominio piu' larga di {@link DominioVisibilita}:
 * un dominio conta come raggiungibile anche con un solo grant parziale su una
 * sua UO, non solo con un dominio "intero" (V1: {@code Utenza.getIdDomini()}/
 * {@code isDominioAuthorized} trattano allo stesso modo i due casi). Da usare
 * solo per risorse che hanno un fallback a grana UO per il dettaglio (la
 * lista dei domini stessa, le sue unita' operative) — non per risorse senza
 * asse UO (es. flussi di rendicontazione), che restano su
 * {@link DominioVisibilita}.
 */
public final class DominioRaggiungibilita {

    private DominioRaggiungibilita() {
    }

    public static Predicate predicate(CriteriaBuilder cb, Path<Long> idDominio, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            return cb.conjunction();
        }
        Set<Long> raggiungibili = operatore.idDominiRaggiungibili();
        if (raggiungibili == null || raggiungibili.isEmpty()) {
            return cb.disjunction();
        }
        return idDominio.in(raggiungibili);
    }

    public static boolean isRaggiungibile(Long idDominio, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            return true;
        }
        return idDominio != null && operatore.idDominiRaggiungibili().contains(idDominio);
    }
}
