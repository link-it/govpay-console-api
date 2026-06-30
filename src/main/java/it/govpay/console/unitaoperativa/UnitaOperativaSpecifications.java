package it.govpay.console.unitaoperativa;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.UnitaOperativa;

public final class UnitaOperativaSpecifications {

    private UnitaOperativaSpecifications() {
    }

    public static Specification<UnitaOperativa> byDominioId(Long idDominio) {
        return (root, q, cb) -> cb.equal(root.get("dominio").get("id"), idDominio);
    }

    /** Esclude l'unita' operativa speciale che porta l'anagrafica del dominio. */
    public static Specification<UnitaOperativa> excludeEc(String codUoEc) {
        return (root, q, cb) -> cb.notEqual(root.get("codUo"), codUoEc);
    }

    public static Specification<UnitaOperativa> ragioneSocialePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("uoDenominazione")), pattern);
    }

    public static Specification<UnitaOperativa> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }
}
