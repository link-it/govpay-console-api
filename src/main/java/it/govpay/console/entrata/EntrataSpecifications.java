package it.govpay.console.entrata;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.TipoTributo;
import it.govpay.console.entity.Tributo;
import jakarta.persistence.criteria.Subquery;

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

    /**
     * Restrizione alle entrate del catalogo globale non ancora associate (via
     * {@link Tributo}) al dominio indicato. Il chiamante valida che il
     * dominio sia visibile all'operatore prima di applicare questo predicato:
     * qui si assume gia' verificato.
     *
     * <p>Verifica indici (issue #67, non applicata: stessa analisi di
     * {@code TipoPendenzaSpecifications.nonAssociatiADominio} — {@code tributi}
     * e {@code domini} sono anagrafica di configurazione, non transazionale).
     * Il filtro su {@code codDominio} risolve {@code domini.id} via l'indice
     * unico su {@code cod_dominio}, poi la join di ritorno su {@code tributi}
     * usa {@code id_dominio} come colonna leading dell'indice unico
     * {@code unique_tributi_1 (id_dominio, id_tipo_tributo)}, che copre anche
     * l'uguaglianza su {@code id_tipo_tributo}: nessun nuovo indice proposto.
     *
     * <p>Confermato con {@code EXPLAIN (ANALYZE, BUFFERS)} su dataset sintetico
     * (2.000 domini, 150 tipi tributo, ~8 associazioni/dominio, schema V1
     * reale): stessa shape di {@code TipoPendenzaSpecifications.nonAssociatiADominio}
     * — {@code Index Scan} su {@code unique_domini_1} seguito da
     * {@code Nested Loop} con {@code Index Only Scan} su
     * {@code unique_tributi_1} (0 heap fetches), Hash Anti Join finale col
     * catalogo globale — nessuna scan sequenziale, <0.1ms.
     */
    public static Specification<TipoTributo> nonAssociatiADominio(String codDominio) {
        if (codDominio == null || codDominio.isBlank()) {
            return null;
        }
        return (root, q, cb) -> {
            Subquery<Long> sub = q.subquery(Long.class);
            var tributo = sub.from(Tributo.class);
            sub.select(tributo.get("id"));
            sub.where(
                    cb.equal(tributo.get("tipoTributo").get("id"), root.get("id")),
                    cb.equal(tributo.get("dominio").get("codDominio"), codDominio));
            return cb.not(cb.exists(sub));
        };
    }
}
