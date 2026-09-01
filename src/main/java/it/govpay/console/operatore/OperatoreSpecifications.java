package it.govpay.console.operatore;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.LikePatterns;
import it.govpay.console.entity.Operatore;

public final class OperatoreSpecifications {

    private OperatoreSpecifications() {
    }

    public static Specification<Operatore> principalPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("utenza").get("principalOriginale")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<Operatore> nomePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("nome")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<Operatore> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("utenza").get("abilitato"), value);
    }
}
