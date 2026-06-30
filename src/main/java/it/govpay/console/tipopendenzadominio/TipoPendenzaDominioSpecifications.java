package it.govpay.console.tipopendenzadominio;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.TipoVersamentoDominio;

public final class TipoPendenzaDominioSpecifications {

    private TipoPendenzaDominioSpecifications() {
    }

    public static Specification<TipoVersamentoDominio> byDominioId(Long idDominio) {
        return (root, q, cb) -> cb.equal(root.get("dominio").get("id"), idDominio);
    }

    public static Specification<TipoVersamentoDominio> idTipoPendenzaPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("tipoVersamento").get("codTipoVersamento")), pattern);
    }

    public static Specification<TipoVersamentoDominio> descrizionePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("tipoVersamento").get("descrizione")), pattern);
    }

    public static Specification<TipoVersamentoDominio> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }
}
