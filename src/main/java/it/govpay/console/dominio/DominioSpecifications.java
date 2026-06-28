package it.govpay.console.dominio;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Dominio;

public final class DominioSpecifications {

    private DominioSpecifications() {
    }

    public static Specification<Dominio> codDominioPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codDominio")), pattern);
    }

    public static Specification<Dominio> ragioneSocialePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("ragioneSociale")), pattern);
    }

    public static Specification<Dominio> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }
}
