package it.govpay.console.common;

import java.util.List;

import it.govpay.console.entity.Versamento;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Predicati su {@link Versamento} condivisi fra le risorse che lo raggiungono da
 * punti di ingresso diversi: direttamente ({@code /pendenze}, root della query) o
 * via join ({@code /ricevute}, {@code root.get("versamento")} su {@link
 * it.govpay.console.entity.Rpt}). Stesso pattern di {@link
 * it.govpay.console.security.VersamentoVisibilita#predicate}: {@code versamento} è
 * un {@link Path} generico, non un {@code Root}, proprio per essere componibile da
 * entrambi i chiamanti.
 *
 * <p>Non tutti i filtri su {@code Versamento} vivono qui: solo quelli con semantica
 * identica sulle risorse che li usano. {@code idPendenza} resta locale a ciascuna
 * risorsa perché {@code /pendenze} lo tratta a match parziale e {@code /ricevute} a
 * match esatto — stessa colonna, semantica diversa, nessun riuso possibile.
 */
public final class VersamentoPredicates {

    private VersamentoPredicates() {
    }

    public static Predicate identificativoDebitoreExact(CriteriaBuilder cb, Path<Versamento> versamento, String value) {
        return cb.equal(versamento.get("srcDebitoreIdentificativo"), value);
    }

    public static Predicate idA2AExact(CriteriaBuilder cb, Path<Versamento> versamento, String value) {
        return cb.equal(versamento.get("applicazione").get("codApplicazione"), value);
    }

    /** Semantica OR fra i valori: {@code id_tipo_versamento IN (...)}. */
    public static Predicate idTipoPendenzaIn(CriteriaBuilder cb, Path<Versamento> versamento, List<String> values) {
        return versamento.get("tipoVersamento").get("codTipoVersamento").in(values);
    }

    /** Semantica OR fra i valori: {@code direzione IN (...)}. */
    public static Predicate direzioneIn(CriteriaBuilder cb, Path<Versamento> versamento, List<String> values) {
        return versamento.get("direzione").in(values);
    }

    /** Semantica OR fra i valori: {@code divisione IN (...)}. */
    public static Predicate divisioneIn(CriteriaBuilder cb, Path<Versamento> versamento, List<String> values) {
        return versamento.get("divisione").in(values);
    }
}
