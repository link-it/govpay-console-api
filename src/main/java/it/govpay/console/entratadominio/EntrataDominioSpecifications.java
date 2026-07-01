package it.govpay.console.entratadominio;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Tributo;

public final class EntrataDominioSpecifications {

    private EntrataDominioSpecifications() {
    }

    public static Specification<Tributo> byDominioId(Long idDominio) {
        return (root, q, cb) -> cb.equal(root.get("dominio").get("id"), idDominio);
    }

    public static Specification<Tributo> idEntrataPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("tipoTributo").get("codTributo")), pattern);
    }

    public static Specification<Tributo> descrizionePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("tipoTributo").get("descrizione")), pattern);
    }

    public static Specification<Tributo> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }
}
