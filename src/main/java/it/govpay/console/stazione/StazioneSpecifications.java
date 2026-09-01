package it.govpay.console.stazione;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.LikePatterns;
import it.govpay.console.entity.Stazione;

public final class StazioneSpecifications {

    private StazioneSpecifications() {
    }

    public static Specification<Stazione> byIntermediarioId(Long idIntermediario) {
        return (root, q, cb) -> cb.equal(root.get("intermediario").get("id"), idIntermediario);
    }

    public static Specification<Stazione> codStazionePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codStazione")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<Stazione> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }
}
