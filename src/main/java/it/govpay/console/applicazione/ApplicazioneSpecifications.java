package it.govpay.console.applicazione;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Applicazione;

public final class ApplicazioneSpecifications {

    private ApplicazioneSpecifications() {
    }

    public static Specification<Applicazione> idA2APartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codApplicazione")), pattern);
    }

    public static Specification<Applicazione> principalPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("utenza").get("principalOriginale")), pattern);
    }

    public static Specification<Applicazione> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("utenza").get("abilitato"), value);
    }
}
