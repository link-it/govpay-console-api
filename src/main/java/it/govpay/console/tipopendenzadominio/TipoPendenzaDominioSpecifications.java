package it.govpay.console.tipopendenzadominio;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.LikePatterns;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.TipoVersamentoVisibilita;
import jakarta.persistence.criteria.Predicate;

public final class TipoPendenzaDominioSpecifications {

    private static final String FIELD_TIPO_VERSAMENTO = "tipoVersamento";
    private static final String FIELD_BO_FORM_DEFINIZIONE = "boFormDefinizione";
    private static final String FIELD_BO_FORM_TIPO = "boFormTipo";
    private static final String FIELD_TRAC_CSV_HEADER_RISPOSTA = "tracCsvHeaderRisposta";
    private static final String FIELD_TRAC_CSV_TEMPLATE_RICHIESTA = "tracCsvTemplateRichiesta";
    private static final String FIELD_TRAC_CSV_TEMPLATE_RISPOSTA = "tracCsvTemplateRisposta";
    private static final String FIELD_TRAC_CSV_TIPO = "tracCsvTipo";

    private TipoPendenzaDominioSpecifications() {
    }

    public static Specification<TipoVersamentoDominio> byDominioId(Long idDominio) {
        return (root, q, cb) -> cb.equal(root.get("dominio").get("id"), idDominio);
    }

    public static Specification<TipoVersamentoDominio> idTipoPendenzaPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get(FIELD_TIPO_VERSAMENTO).get("codTipoVersamento")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<TipoVersamentoDominio> descrizionePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get(FIELD_TIPO_VERSAMENTO).get("descrizione")), pattern, LikePatterns.ESCAPE_CHAR);
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
     *
     * <p>Verifica indici (issue #67): il filtro è sempre scoped a
     * {@code id_dominio} dal chiamante (vedi {@link #byDominioId}), quindi
     * anche col predicato OR fra override e globale il piano parte da
     * {@code Index Scan} su {@code unique_tipi_vers_domini_1
     * (id_dominio, id_tipo_versamento)} e usa {@code tipi_versamento} solo
     * come Hash Join per la parte "eredità dal globale". Confermato con
     * {@code EXPLAIN (ANALYZE, BUFFERS)} su dataset sintetico (2.000 domini,
     * ~12 associazioni/dominio, schema V1 reale): nessuna scan completa su
     * {@code tipi_vers_domini} nonostante l'OR fra le due entità, nessun
     * nuovo indice proposto.
     */
    public static Specification<TipoVersamentoDominio> formExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> {
            var tv = root.get(FIELD_TIPO_VERSAMENTO);
            Predicate override = cb.and(cb.isNotNull(root.get(FIELD_BO_FORM_DEFINIZIONE)), cb.isNotNull(root.get(FIELD_BO_FORM_TIPO)));
            Predicate overrideAssente = cb.and(cb.isNull(root.get(FIELD_BO_FORM_DEFINIZIONE)), cb.isNull(root.get(FIELD_BO_FORM_TIPO)));
            Predicate globale = cb.and(cb.isNotNull(tv.get(FIELD_BO_FORM_DEFINIZIONE)), cb.isNotNull(tv.get(FIELD_BO_FORM_TIPO)));
            Predicate globaleAssente = cb.and(cb.isNull(tv.get(FIELD_BO_FORM_DEFINIZIONE)), cb.isNull(tv.get(FIELD_BO_FORM_TIPO)));
            return value
                    ? cb.or(override, cb.and(globale, overrideAssente))
                    : cb.and(overrideAssente, globaleAssente);
        };
    }

    /**
     * Presenza dei template di trasformazione CSV, con la stessa eredita' dal
     * globale di {@link #formExact} (vedi Javadoc li') sulle quattro colonne
     * {@code trac_csv_*}. Stessa verifica indici di {@link #formExact}: piano
     * identico (query di forma equivalente, quattro colonne invece di due non
     * cambia lo shape del piano).
     */
    public static Specification<TipoVersamentoDominio> trasformazioneExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> {
            var tv = root.get(FIELD_TIPO_VERSAMENTO);
            Predicate override = cb.and(
                    cb.isNotNull(root.get(FIELD_TRAC_CSV_HEADER_RISPOSTA)),
                    cb.isNotNull(root.get(FIELD_TRAC_CSV_TEMPLATE_RICHIESTA)),
                    cb.isNotNull(root.get(FIELD_TRAC_CSV_TEMPLATE_RISPOSTA)),
                    cb.isNotNull(root.get(FIELD_TRAC_CSV_TIPO)));
            Predicate overrideAssente = cb.and(
                    cb.isNull(root.get(FIELD_TRAC_CSV_HEADER_RISPOSTA)),
                    cb.isNull(root.get(FIELD_TRAC_CSV_TEMPLATE_RICHIESTA)),
                    cb.isNull(root.get(FIELD_TRAC_CSV_TEMPLATE_RISPOSTA)),
                    cb.isNull(root.get(FIELD_TRAC_CSV_TIPO)));
            Predicate globale = cb.and(
                    cb.isNotNull(tv.get(FIELD_TRAC_CSV_HEADER_RISPOSTA)),
                    cb.isNotNull(tv.get(FIELD_TRAC_CSV_TEMPLATE_RICHIESTA)),
                    cb.isNotNull(tv.get(FIELD_TRAC_CSV_TEMPLATE_RISPOSTA)),
                    cb.isNotNull(tv.get(FIELD_TRAC_CSV_TIPO)));
            Predicate globaleAssente = cb.and(
                    cb.isNull(tv.get(FIELD_TRAC_CSV_HEADER_RISPOSTA)),
                    cb.isNull(tv.get(FIELD_TRAC_CSV_TEMPLATE_RICHIESTA)),
                    cb.isNull(tv.get(FIELD_TRAC_CSV_TEMPLATE_RISPOSTA)),
                    cb.isNull(tv.get(FIELD_TRAC_CSV_TIPO)));
            return value
                    ? cb.or(override, cb.and(globale, overrideAssente))
                    : cb.and(overrideAssente, globaleAssente);
        };
    }

    /**
     * ACL sul tipo versamento associato. V1 ({@code DominiController.findTipiPendenza})
     * la applica solo con {@code associati=true} esplicito, qui invece e' sempre
     * in AND — hardening deliberato sempre in AND"), non una replica letterale del default V1.
     */
    public static Specification<TipoVersamentoDominio> visibiliPerOperatore(OperatoreCorrente operatore) {
        return (root, q, cb) -> TipoVersamentoVisibilita.predicate(cb, root.get(FIELD_TIPO_VERSAMENTO).get("id"), operatore);
    }
}
