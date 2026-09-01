package it.govpay.console.tracciato;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.LikePatterns;
import it.govpay.console.entity.Tracciato;
import it.govpay.console.model.FormatoTracciato;
import it.govpay.console.model.StatoTracciatoPendenza;
import it.govpay.console.security.DominioVisibilita;
import it.govpay.console.security.OperatoreCorrente;
import jakarta.persistence.criteria.JoinType;

public final class TracciatoSpecifications {

    private TracciatoSpecifications() {
    }

    public static Specification<Tracciato> idDominioExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("dominio").get("codDominio"), value);
    }

    public static Specification<Tracciato> statoExact(StatoTracciatoPendenza value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> {
            var statoPredicate = cb.equal(root.get("stato"), TracciatoStatoMapper.statoDbFor(value));
            String likePattern = TracciatoStatoMapper.beanDatiLikePattern(value);
            return likePattern == null ? statoPredicate : cb.and(statoPredicate, cb.like(root.get("beanDati"), likePattern));
        };
    }

    public static Specification<Tracciato> dataDaInclusive(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("dataCaricamento"), value);
    }

    public static Specification<Tracciato> dataAInclusive(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("dataCaricamento"), value);
    }

    /** Match parziale case-insensitive sul principal (join espliciti LEFT: id_operatore e' nullable). */
    public static Specification<Tracciato> operatoreMittentePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> {
            var operatoreJoin = root.join("operatore", JoinType.LEFT);
            var utenzaJoin = operatoreJoin.join("utenza", JoinType.LEFT);
            return cb.like(cb.lower(utenzaJoin.get("principal")), pattern, LikePatterns.ESCAPE_CHAR);
        };
    }

    public static Specification<Tracciato> formatoExact(FormatoTracciato value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("formato"), value.getValue());
    }

    /** Delega la regola ACL a {@link DominioVisibilita}, stesso criterio dei flussi di rendicontazione. */
    public static Specification<Tracciato> visibiliPerOperatore(OperatoreCorrente operatore) {
        return (root, q, cb) -> DominioVisibilita.predicate(cb, root.get("dominio").get("id"), operatore);
    }
}
