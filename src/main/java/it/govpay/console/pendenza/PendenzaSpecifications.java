package it.govpay.console.pendenza;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.GiornoDatabase;
import it.govpay.console.common.LikePatterns;
import it.govpay.console.common.PendenzaPredicates;
import it.govpay.console.entity.Dominio;
import it.govpay.console.model.StatoPendenza;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.PendenzaVisibilita;
import it.govpay.pendenze.entity.Pendenza;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Filtri della lista pendenze sul modello v3. La pendenza ha chiavi piatte verso dominio,
 * applicazione e tipo, risolte con sottoquery; debitore, data di pubblicazione e unita'
 * operativa stanno invece sul percorso {@code opzione di pagamento -> debitore della
 * posizione -> posizione}, che ogni query percorre con un unico join interno.
 */
public final class PendenzaSpecifications {

    private static final String FIELD_STATO = "stato";
    private static final String FIELD_DATA_SCADENZA = "dataScadenzaAvviso";
    private static final String FIELD_DATA_PUBBLICAZIONE = "dataPubblicazione";
    private static final String FIELD_ID = "id";

    private PendenzaSpecifications() {
    }

    /**
     * Esclude le pendenze senza opzione di pagamento, che il modello non ammette: stessa
     * condizione del dettaglio, cosi' ogni riga della lista si puo' aprire.
     */
    public static Specification<Pendenza> conOpzione() {
        return (root, q, cb) -> {
            posizione(root);
            return cb.conjunction();
        };
    }

    public static Specification<Pendenza> idPendenzaPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("idPendenza")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<Pendenza> numeroAvvisoExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("numeroAvviso"), value);
    }

    public static Specification<Pendenza> idDominioExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> {
            Subquery<Long> sub = q.subquery(Long.class);
            Root<Dominio> dominio = sub.from(Dominio.class);
            sub.select(dominio.get(FIELD_ID)).where(cb.equal(dominio.get("codDominio"), value));
            return root.<Long>get("idDominio").in(sub);
        };
    }

    /**
     * Debitore dell'opzione di pagamento della pendenza, senza distinguere maiuscole: la
     * ricerca restituisce le pendenze di quel debitore e non di tutta la posizione.
     */
    public static Specification<Pendenza> identificativoDebitoreExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> PendenzaPredicates.identificativoDebitoreExact(cb,
                pdDebitore(root).get("debitore"), value);
    }

    /** Limite inferiore incluso sulla data di creazione ({@code data_creazione}). */
    public static Specification<Pendenza> dataCreazioneDa(LocalDate da) {
        if (da == null) {
            return null;
        }
        OffsetDateTime from = GiornoDatabase.inizio(da);
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("dataCreazione"), from);
    }

    /** Limite superiore incluso: {@code data_creazione < (dataA + 1 giorno)}. */
    public static Specification<Pendenza> dataCreazioneA(LocalDate a) {
        if (a == null) {
            return null;
        }
        OffsetDateTime toExclusive = GiornoDatabase.inizio(a.plusDays(1));
        return (root, q, cb) -> cb.lessThan(root.get("dataCreazione"), toExclusive);
    }

    public static Specification<Pendenza> iuvExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("iuv"), value);
    }

    /** Semantica OR fra i valori: {@code versamenti.direzione IN (...)}. */
    public static Specification<Pendenza> direzioneIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> PendenzaPredicates.direzioneIn(root, values);
    }

    /** Semantica OR fra i valori: {@code versamenti.divisione IN (...)}. */
    public static Specification<Pendenza> divisioneIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> PendenzaPredicates.divisioneIn(root, values);
    }

    public static Specification<Pendenza> idA2AExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> PendenzaPredicates.idA2AExact(cb, q, root, value);
    }

    /** Semantica OR fra i codici: {@code id_tipo_versamento IN (tipi con quel codice)}. */
    public static Specification<Pendenza> idTipoPendenzaIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> PendenzaPredicates.idTipoPendenzaIn(cb, q, root, values);
    }

    /**
     * Traduce lo stato esposto sul modello v3, con le stesse regole di derivazione del
     * mapper. {@code NON_ESEGUITO} e' l'unico stato che si deriva, e solo se la posizione e'
     * pubblicata: {@code NON_PUBBLICATA} prevale su {@code SCADUTO}, e {@code SCADUTO} e'
     * {@code NON_ESEGUITO} con scadenza passata. Gli altri stati sono quelli della pendenza.
     */
    public static Specification<Pendenza> statoExact(StatoPendenza stato, OffsetDateTime now, LocalDate oggi) {
        if (stato == null) {
            return null;
        }
        return switch (stato) {
            case NON_PUBBLICATA -> (root, q, cb) -> cb.and(
                    nonEseguito(root, cb), cb.not(pubblicata(root, cb, oggi)));
            case NON_ESEGUITO -> (root, q, cb) -> cb.and(
                    nonEseguito(root, cb), pubblicata(root, cb, oggi),
                    cb.or(cb.isNull(root.get(FIELD_DATA_SCADENZA)),
                            cb.greaterThanOrEqualTo(root.get(FIELD_DATA_SCADENZA), now)));
            case SCADUTO -> (root, q, cb) -> cb.and(
                    nonEseguito(root, cb), pubblicata(root, cb, oggi),
                    cb.isNotNull(root.get(FIELD_DATA_SCADENZA)),
                    cb.lessThan(root.get(FIELD_DATA_SCADENZA), now));
            default -> {
                it.govpay.pendenze.model.StatoPendenza diretto =
                        it.govpay.pendenze.model.StatoPendenza.valueOf(stato.name());
                yield (root, q, cb) -> cb.equal(root.get(FIELD_STATO), diretto);
            }
        };
    }

    /**
     * Limita i risultati alle pendenze visibili all'operatore corrente. La regola e' quella
     * di {@link PendenzaVisibilita}, single-source con il dettaglio.
     *
     * @param dominiConUoEcVisibile domini di cui l'operatore ha la unita' operativa {@code EC}
     */
    public static Specification<Pendenza> visibiliPerOperatore(OperatoreCorrente operatore,
                                                               Set<Long> dominiConUoEcVisibile) {
        return (root, q, cb) -> PendenzaVisibilita.predicate(cb,
                root.<Long>get("idDominio"),
                posizione(root).<Long>get("idUnitaOperativa"),
                root.<Long>get("idTipoVersamento"),
                dominiConUoEcVisibile, operatore);
    }

    private static Predicate nonEseguito(Root<Pendenza> root, CriteriaBuilder cb) {
        return cb.equal(root.get(FIELD_STATO), it.govpay.pendenze.model.StatoPendenza.NON_ESEGUITO);
    }

    private static Predicate pubblicata(Root<Pendenza> root, CriteriaBuilder cb, LocalDate oggi) {
        Path<LocalDate> dataPubblicazione = posizione(root).get(FIELD_DATA_PUBBLICAZIONE);
        return cb.or(cb.isNull(dataPubblicazione), cb.lessThanOrEqualTo(dataPubblicazione, oggi));
    }

    private static Join<?, ?> pdDebitore(Root<Pendenza> root) {
        return join(join(root, "opzionePagamento"), "pdDebitore");
    }

    private static Join<?, ?> posizione(Root<Pendenza> root) {
        return join(pdDebitore(root), "posizioneDebitoria");
    }

    /** Join interno su {@code attributo}, riusato se la query lo ha gia' (un solo join per percorso). */
    private static Join<?, ?> join(From<?, ?> from, String attributo) {
        for (Join<?, ?> esistente : from.getJoins()) {
            if (esistente.getAttribute().getName().equals(attributo) && esistente.getJoinType() == JoinType.INNER) {
                return esistente;
            }
        }
        return from.join(attributo, JoinType.INNER);
    }
}
