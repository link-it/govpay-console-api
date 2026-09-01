package it.govpay.console.pendenza;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.LikePatterns;
import it.govpay.console.common.VersamentoPredicates;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.StatoPendenza;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.VersamentoVisibilita;

public final class PendenzaSpecifications {

    private PendenzaSpecifications() {
    }

    public static Specification<Versamento> idPendenzaPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codVersamentoEnte")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<Versamento> numeroAvvisoExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("numeroAvviso"), value);
    }

    public static Specification<Versamento> idDominioExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("dominio").get("codDominio"), value);
    }

    public static Specification<Versamento> identificativoDebitoreExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.identificativoDebitoreExact(cb, root, value);
    }

    /** Limite inferiore incluso sulla data di creazione ({@code data_creazione}). */
    public static Specification<Versamento> dataCreazioneDa(LocalDate da) {
        if (da == null) {
            return null;
        }
        OffsetDateTime from = da.atStartOfDay().atOffset(ZoneOffset.UTC);
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("dataCreazione"), from);
    }

    /** Limite superiore incluso: {@code data_creazione < (dataA + 1 giorno)}. */
    public static Specification<Versamento> dataCreazioneA(LocalDate a) {
        if (a == null) {
            return null;
        }
        OffsetDateTime toExclusive = a.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return (root, q, cb) -> cb.lessThan(root.get("dataCreazione"), toExclusive);
    }

    public static Specification<Versamento> iuvExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("iuvVersamento"), value);
    }

    /**
     * Semantica OR fra i valori: {@code versamenti.direzione IN (...)}. Cardinalita'
     * allineata a {@code /ricevute} (issue #68): stessa colonna, stesso predicato
     * condiviso via {@link VersamentoPredicates}, deliberatamente piu' larga del
     * match esatto originario di questa issue (#66) — V1 stesso trattava
     * {@code direzione}/{@code divisione} in modo incoerente fra {@code /pendenze}
     * (match esatto) e {@code /rpp} (lista): qui si sceglie la lista per entrambe le
     * risorse V2, non la replica letterale di una delle due.
     *
     * <p>Verifica indici (issue #66, non applicata: lo schema di {@code versamenti}
     * e' condiviso col core, la migrazione va concordata a parte). Sul DDL V1
     * reale, {@code direzione}/{@code divisione} non hanno alcun indice: se
     * usati in isolamento (senza {@code idDominio}, gia' indicizzato) il filtro
     * fa scan completa. Proposta se l'uso reale risultera' selettivo:
     * {@code CREATE INDEX idx_vrs_direzione ON versamenti (direzione);}
     * {@code CREATE INDEX idx_vrs_divisione ON versamenti (divisione);}
     */
    public static Specification<Versamento> direzioneIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.direzioneIn(cb, root, values);
    }

    /** Semantica OR fra i valori: {@code versamenti.divisione IN (...)}. Vedi {@link #direzioneIn}. */
    public static Specification<Versamento> divisioneIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.divisioneIn(cb, root, values);
    }

    /**
     * Verifica indici (issue #66, non applicata: vedi nota su {@link #direzioneIn}).
     * {@code id_applicazione} non ha un indice con se stesso come colonna leading
     * (solo 2a colonna in {@code idx_vrs_id_pendenza(cod_versamento_ente, id_applicazione)}):
     * un {@code idA2A} senza {@code idDominio} fa scan. Proposta:
     * {@code CREATE INDEX idx_vrs_id_applicazione ON versamenti (id_applicazione);}
     */
    public static Specification<Versamento> idA2AExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.idA2AExact(cb, root, value);
    }

    /**
     * Semantica OR fra i valori: {@code versamenti.id_tipo_versamento IN (...)}.
     *
     * <p>Verifica indici (issue #66, non applicata: vedi nota su {@link #direzioneIn}).
     * {@code id_tipo_versamento} non ha un indice con se stesso come colonna leading
     * (solo 2a colonna in {@code idx_vrs_auth(id_dominio, id_tipo_versamento, id_uo)}):
     * un {@code idTipoPendenza} senza {@code idDominio} fa scan. Proposta:
     * {@code CREATE INDEX idx_vrs_id_tipo_versamento ON versamenti (id_tipo_versamento);}
     */
    public static Specification<Versamento> idTipoPendenzaIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.idTipoPendenzaIn(cb, root, values);
    }

    /**
     * Traduce lo stato V2 sul/i valore/i grezzo/i di {@code stato_versamento},
     * condividendo i gruppi con {@link PendenzaMapper} tramite {@link StatoVersamentoMapping}
     * (fonte unica: le due derivazioni non possono piu' divergere silenziosamente).
     * V1 non e' consistente sul genere del valore grezzo (visto sia
     * {@code ESEGUITO} che {@code ESEGUITA} in dati reali): ogni stato include
     * tutte le varianti riconosciute — un filtro piu' stretto lascerebbe fuori
     * righe che l'output mostra correttamente mappate.
     * {@code PAGATA} include anche gli stati interni equivalenti
     * ({@code ESEGUITO_ALTRO_CANALE}, {@code ESEGUITO_SENZA_RPT}).
     * {@code SCADUTA} unisce il valore letterale ({@code SCADUTA}/{@code SCADUTO})
     * con la derivazione da {@code NON_ESEGUITO} + {@code data_scadenza} passata —
     * stessa semantica di V1 (V1 {@code PendenzeDAO}:
     * {@code AbilitaFiltroNonScaduto}/{@code AbilitaFiltroScaduto}) per la parte
     * derivata. {@code ANOMALA} e' il catch-all del mapper (valori letterali
     * {@code ANOMALA}/{@code ANOMALO} + qualunque valore non riconosciuto): il
     * filtro lo esprime come {@code NOT IN} sul complemento, non un elenco chiuso,
     * altrimenti una riga con stato grezzo ignoto sarebbe mostrata ANOMALA in
     * output ma irraggiungibile da {@code ?stato=ANOMALA}.
     */
    public static Specification<Versamento> statoExact(StatoPendenza stato, OffsetDateTime now) {
        if (stato == null) {
            return null;
        }
        return switch (stato) {
            case PAGATA -> (root, q, cb) -> root.<String>get("statoVersamento").in(StatoVersamentoMapping.PAGATA);
            case PAGATA_PARZIALE -> (root, q, cb) ->
                    root.<String>get("statoVersamento").in(StatoVersamentoMapping.PAGATA_PARZIALE);
            case RICONCILIATA -> (root, q, cb) ->
                    root.<String>get("statoVersamento").in(StatoVersamentoMapping.RICONCILIATA);
            case ANNULLATA -> (root, q, cb) -> root.<String>get("statoVersamento").in(StatoVersamentoMapping.ANNULLATA);
            case ANOMALA -> (root, q, cb) ->
                    cb.not(root.<String>get("statoVersamento").in(StatoVersamentoMapping.ALTRI_STATI_NOTI));
            case NON_PAGATA -> (root, q, cb) -> cb.and(
                    root.<String>get("statoVersamento").in(StatoVersamentoMapping.NON_ESEGUITO),
                    cb.or(cb.isNull(root.get("dataScadenza")), cb.greaterThanOrEqualTo(root.get("dataScadenza"), now)));
            case SCADUTA -> (root, q, cb) -> cb.or(
                    root.<String>get("statoVersamento").in(StatoVersamentoMapping.SCADUTA_LETTERALE),
                    cb.and(
                            root.<String>get("statoVersamento").in(StatoVersamentoMapping.NON_ESEGUITO),
                            cb.isNotNull(root.get("dataScadenza")),
                            cb.lessThan(root.get("dataScadenza"), now)));
        };
    }

    /**
     * Limita i risultati alle pendenze visibili all'operatore corrente. Delega la
     * regola ACL alla single-source {@link VersamentoVisibilita}.
     */
    public static Specification<Versamento> visibiliPerOperatore(OperatoreCorrente operatore) {
        return (root, q, cb) -> VersamentoVisibilita.predicate(cb, root, operatore);
    }
}
