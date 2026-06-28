package it.govpay.console.tipopendenza;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.TipoVersamento;

public final class TipoPendenzaSpecifications {

    private TipoPendenzaSpecifications() {
    }

    public static Specification<TipoVersamento> codTipoVersamentoPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codTipoVersamento")), pattern);
    }

    public static Specification<TipoVersamento> descrizionePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("descrizione")), pattern);
    }

    public static Specification<TipoVersamento> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }
}
