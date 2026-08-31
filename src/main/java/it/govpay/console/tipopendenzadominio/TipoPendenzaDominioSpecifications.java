package it.govpay.console.tipopendenzadominio;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.TipoVersamentoDominio;
import jakarta.persistence.criteria.Predicate;

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

    /**
     * Presenza della form di inserimento custom, con l'eredita' dal tipo
     * pendenza globale che V1 applica quando il dominio non ridefinisce nulla
     * (verificato su {@code TipoVersamentoDominioFilter}, non sulla stessa
     * classe usata dalla lista globale): {@code true} = override presente,
     * oppure override assente e il globale valorizzato; {@code false} =
     * nessuna form a nessuno dei due livelli. Non e' un semplice "colonne di
     * {@code TipoVersamentoDominio} nulle", altrimenti un'associazione senza
     * override ma con form globale verrebbe esclusa da {@code form=true} e
     * inclusa (erroneamente) da {@code form=false}.
     */
    public static Specification<TipoVersamentoDominio> formExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> {
            var tv = root.get("tipoVersamento");
            Predicate override = cb.and(cb.isNotNull(root.get("boFormDefinizione")), cb.isNotNull(root.get("boFormTipo")));
            Predicate overrideAssente = cb.and(cb.isNull(root.get("boFormDefinizione")), cb.isNull(root.get("boFormTipo")));
            Predicate globale = cb.and(cb.isNotNull(tv.get("boFormDefinizione")), cb.isNotNull(tv.get("boFormTipo")));
            Predicate globaleAssente = cb.and(cb.isNull(tv.get("boFormDefinizione")), cb.isNull(tv.get("boFormTipo")));
            return value
                    ? cb.or(override, cb.and(globale, overrideAssente))
                    : cb.and(overrideAssente, globaleAssente);
        };
    }

    /**
     * Presenza dei template di trasformazione CSV, con la stessa eredita' dal
     * globale di {@link #formExact} (vedi Javadoc li') sulle quattro colonne
     * {@code trac_csv_*}.
     */
    public static Specification<TipoVersamentoDominio> trasformazioneExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> {
            var tv = root.get("tipoVersamento");
            Predicate override = cb.and(
                    cb.isNotNull(root.get("tracCsvHeaderRisposta")),
                    cb.isNotNull(root.get("tracCsvTemplateRichiesta")),
                    cb.isNotNull(root.get("tracCsvTemplateRisposta")),
                    cb.isNotNull(root.get("tracCsvTipo")));
            Predicate overrideAssente = cb.and(
                    cb.isNull(root.get("tracCsvHeaderRisposta")),
                    cb.isNull(root.get("tracCsvTemplateRichiesta")),
                    cb.isNull(root.get("tracCsvTemplateRisposta")),
                    cb.isNull(root.get("tracCsvTipo")));
            Predicate globale = cb.and(
                    cb.isNotNull(tv.get("tracCsvHeaderRisposta")),
                    cb.isNotNull(tv.get("tracCsvTemplateRichiesta")),
                    cb.isNotNull(tv.get("tracCsvTemplateRisposta")),
                    cb.isNotNull(tv.get("tracCsvTipo")));
            Predicate globaleAssente = cb.and(
                    cb.isNull(tv.get("tracCsvHeaderRisposta")),
                    cb.isNull(tv.get("tracCsvTemplateRichiesta")),
                    cb.isNull(tv.get("tracCsvTemplateRisposta")),
                    cb.isNull(tv.get("tracCsvTipo")));
            return value
                    ? cb.or(override, cb.and(globale, overrideAssente))
                    : cb.and(overrideAssente, globaleAssente);
        };
    }
}
