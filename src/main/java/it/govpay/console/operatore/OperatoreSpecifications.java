package it.govpay.console.operatore;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Operatore;

public final class OperatoreSpecifications {

    private OperatoreSpecifications() {
    }

    public static Specification<Operatore> principalPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("utenza").get("principalOriginale")), pattern);
    }

    public static Specification<Operatore> nomePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("nome")), pattern);
    }

    public static Specification<Operatore> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("utenza").get("abilitato"), value);
    }
}
