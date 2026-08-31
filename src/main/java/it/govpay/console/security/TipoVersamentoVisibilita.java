package it.govpay.console.security;

import java.util.Set;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Regola di visibilita' ACL sui tipi pendenza (V1: {@code isTipoVersamentoAuthorized}/
 * {@code getIdTipiVersamentoAutorizzati}), stesso schema di {@link DominioVisibilita}
 * ma sull'asse tipo versamento invece che dominio.
 */
public final class TipoVersamentoVisibilita {

    private TipoVersamentoVisibilita() {
    }

    public static Predicate predicate(CriteriaBuilder cb, Path<Long> idTipoVersamento, OperatoreCorrente operatore) {
        if (operatore.tuttiITipiVersamento()) {
            return cb.conjunction();
        }
        Set<Long> visibili = operatore.idTipiVersamentoVisibili();
        if (visibili == null || visibili.isEmpty()) {
            return cb.disjunction();
        }
        return idTipoVersamento.in(visibili);
    }

    public static boolean isVisibile(Long idTipoVersamento, OperatoreCorrente operatore) {
        if (operatore.tuttiITipiVersamento()) {
            return true;
        }
        return idTipoVersamento != null && operatore.idTipiVersamentoVisibili().contains(idTipoVersamento);
    }
}
