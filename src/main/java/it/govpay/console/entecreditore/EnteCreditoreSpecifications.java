package it.govpay.console.entecreditore;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.EnteCreditoreCache;

public final class EnteCreditoreSpecifications {

    private EnteCreditoreSpecifications() {
    }

    /**
     * Match parziale, in OR, su codice fiscale e denominazione: unico filtro di
     * ricerca esposto (`?search=`), pensato per il typeahead.
     */
    public static Specification<EnteCreditoreCache> searchPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("codFiscale")), pattern),
                cb.like(cb.lower(root.get("denominazione")), pattern));
    }
}
