package it.govpay.console.common;

import java.util.List;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.pendenze.entity.Pendenza;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Predicati sulla {@link Pendenza} condivisi fra le risorse che la raggiungono da punti di
 * ingresso diversi: direttamente ({@code /pendenze}, root della query) o via join
 * ({@code /ricevute}, join su {@code rpt.id_versamento}). Il percorso e' un {@link Path}
 * generico, non un {@code Root}, proprio per essere componibile da entrambi i chiamanti.
 *
 * <p>La pendenza ha chiavi piatte verso applicazione e tipo: si risolvono con sottoquery.
 * Non tutti i filtri vivono qui: solo quelli con semantica identica sulle risorse che li usano
 * ({@code idPendenza} resta locale: a match parziale su {@code /pendenze}, esatto su
 * {@code /ricevute}).
 */
public final class PendenzaPredicates {

    private static final String FIELD_ID = "id";

    private PendenzaPredicates() {
    }

    public static Predicate idA2AExact(CriteriaBuilder cb, CriteriaQuery<?> q, Path<Pendenza> pendenza,
                                       String value) {
        Subquery<Long> sub = q.subquery(Long.class);
        Root<Applicazione> applicazione = sub.from(Applicazione.class);
        sub.select(applicazione.get(FIELD_ID)).where(cb.equal(applicazione.get("codApplicazione"), value));
        return pendenza.<Long>get("idApplicazione").in(sub);
    }

    /** Semantica OR fra i codici: {@code id_tipo_versamento IN (tipi con quel codice)}. */
    public static Predicate idTipoPendenzaIn(CriteriaBuilder cb, CriteriaQuery<?> q, Path<Pendenza> pendenza,
                                             List<String> values) {
        Subquery<Long> sub = q.subquery(Long.class);
        Root<TipoVersamento> tipo = sub.from(TipoVersamento.class);
        sub.select(tipo.get(FIELD_ID)).where(tipo.get("codTipoVersamento").in(values));
        return pendenza.<Long>get("idTipoVersamento").in(sub);
    }

    /** Semantica OR fra i valori: {@code direzione IN (...)}. */
    public static Predicate direzioneIn(Path<Pendenza> pendenza, List<String> values) {
        return pendenza.<String>get("direzione").in(values);
    }

    /** Semantica OR fra i valori: {@code divisione IN (...)}. */
    public static Predicate divisioneIn(Path<Pendenza> pendenza, List<String> values) {
        return pendenza.<String>get("divisione").in(values);
    }

    /** Identificativo del debitore dell'opzione, senza distinguere maiuscole. */
    public static Predicate identificativoDebitoreExact(CriteriaBuilder cb, Path<?> debitore, String value) {
        return cb.equal(cb.upper(debitore.<String>get("identificativo")), value.toUpperCase());
    }
}
