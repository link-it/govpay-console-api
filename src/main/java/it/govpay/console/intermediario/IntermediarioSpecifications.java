package it.govpay.console.intermediario;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Intermediario;

public final class IntermediarioSpecifications {

    private IntermediarioSpecifications() {
    }

    public static Specification<Intermediario> codIntermediarioPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codIntermediario")), pattern);
    }

    public static Specification<Intermediario> denominazionePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("denominazione")), pattern);
    }

    public static Specification<Intermediario> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }
}
