package it.govpay.console.contoaccredito;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.IbanAccredito;

public final class ContoAccreditoSpecifications {

    private ContoAccreditoSpecifications() {
    }

    public static Specification<IbanAccredito> byDominioId(Long idDominio) {
        return (root, q, cb) -> cb.equal(root.get("dominio").get("id"), idDominio);
    }

    public static Specification<IbanAccredito> descrizionePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("descrizione")), pattern);
    }

    public static Specification<IbanAccredito> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }
}
