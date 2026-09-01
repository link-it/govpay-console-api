package it.govpay.console.tipopendenza;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.LikePatterns;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.TipoVersamentoVisibilita;
import jakarta.persistence.criteria.Subquery;

public final class TipoPendenzaSpecifications {

    private TipoPendenzaSpecifications() {
    }

    public static Specification<TipoVersamento> codTipoVersamentoPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codTipoVersamento")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<TipoVersamento> descrizionePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("descrizione")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<TipoVersamento> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }

    /**
     * Presenza della form di inserimento custom: entrambe le colonne che la
     * definiscono valorizzate (V1: {@code formBackoffice}).
     */
    public static Specification<TipoVersamento> formExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> value
                ? cb.and(cb.isNotNull(root.get("boFormDefinizione")), cb.isNotNull(root.get("boFormTipo")))
                : cb.and(cb.isNull(root.get("boFormDefinizione")), cb.isNull(root.get("boFormTipo")));
    }

    /**
     * Presenza dei template di trasformazione CSV: le quattro colonne che li
     * definiscono tutte valorizzate.
     */
    public static Specification<TipoVersamento> trasformazioneExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> value
                ? cb.and(
                        cb.isNotNull(root.get("tracCsvHeaderRisposta")),
                        cb.isNotNull(root.get("tracCsvTemplateRichiesta")),
                        cb.isNotNull(root.get("tracCsvTemplateRisposta")),
                        cb.isNotNull(root.get("tracCsvTipo")))
                : cb.and(
                        cb.isNull(root.get("tracCsvHeaderRisposta")),
                        cb.isNull(root.get("tracCsvTemplateRichiesta")),
                        cb.isNull(root.get("tracCsvTemplateRisposta")),
                        cb.isNull(root.get("tracCsvTipo")));
    }

    /**
     * Restrizione ai tipi pendenza del catalogo globale non ancora associati
     * (via {@link TipoVersamentoDominio}) al dominio indicato. Il chiamante
     * valida che il dominio sia visibile all'operatore prima di applicare
     * questo predicato: qui si assume gia' verificato.
     *
     * <p>Verifica indici (issue #67, non applicata: {@code tipi_vers_domini} e
     * {@code domini} sono anagrafica di configurazione, non transazionale —
     * ordini di grandezza lontani da {@code versamenti}). Il filtro passa per
     * {@code codDominio} (non per l'id tecnico, che il chiamante risolve solo
     * per l'ACL) cosi' un dominio inesistente produce "nessun match" anziche'
     * un errore, preservando la semantica V1 di "tutti i risultati": comporta
     * una join {@code tipi_vers_domini -> domini} sulla FK {@code id_dominio}
     * anziche' un filtro diretto su quella colonna. Una volta risolto
     * {@code domini.id} (lookup su {@code cod_dominio}, indice unico), la join
     * di ritorno usa {@code id_dominio} come colonna leading dell'indice unico
     * {@code unique_tipi_vers_domini_1 (id_dominio, id_tipo_versamento)}, che
     * copre anche l'uguaglianza su {@code id_tipo_versamento}: nessuna scan
     * completa, nessun nuovo indice proposto.
     *
     * <p>Confermato con {@code EXPLAIN (ANALYZE, BUFFERS)} su dataset sintetico
     * (2.000 domini, 150 tipi versamento, ~12 associazioni/dominio, schema V1
     * reale): {@code Index Scan} su {@code unique_domini_1} seguito da
     * {@code Nested Loop} con {@code Index Only Scan} su
     * {@code unique_tipi_vers_domini_1} (0 heap fetches), Hash Anti Join finale
     * col catalogo globale — nessuna scan sequenziale, <0.1ms.
     */
    public static Specification<TipoVersamento> nonAssociatiADominio(String codDominio) {
        if (codDominio == null || codDominio.isBlank()) {
            return null;
        }
        return (root, q, cb) -> {
            Subquery<Long> sub = q.subquery(Long.class);
            var tvd = sub.from(TipoVersamentoDominio.class);
            sub.select(tvd.get("id"));
            sub.where(
                    cb.equal(tvd.get("tipoVersamento").get("id"), root.get("id")),
                    cb.equal(tvd.get("dominio").get("codDominio"), codDominio));
            return cb.not(cb.exists(sub));
        };
    }

    public static Specification<TipoVersamento> visibiliPerOperatore(OperatoreCorrente operatore) {
        return (root, q, cb) -> TipoVersamentoVisibilita.predicate(cb, root.get("id"), operatore);
    }
}
