package it.govpay.console.entrata;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.TipoTributo;

public final class EntrataSpecifications {

    private EntrataSpecifications() {
    }

    public static Specification<TipoTributo> codTributoPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codTributo")), pattern);
    }

    public static Specification<TipoTributo> descrizionePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("descrizione")), pattern);
    }
}
