package it.govpay.console.pendenza;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

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
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codVersamentoEnte")), pattern);
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
        return (root, q, cb) -> cb.equal(root.get("srcDebitoreIdentificativo"), value);
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

    public static Specification<Versamento> direzioneExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("direzione"), value);
    }

    public static Specification<Versamento> divisioneExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("divisione"), value);
    }

    private static final List<String> RAW_PAGATA =
            List.of("ESEGUITA", "ESEGUITO", "PAGATA", "PAGATO", "ESEGUITO_ALTRO_CANALE", "ESEGUITO_SENZA_RPT");
    private static final List<String> RAW_NON_ESEGUITO =
            List.of("NON_ESEGUITA", "NON_ESEGUITO", "NON_PAGATA", "NON_PAGATO");
    private static final List<String> RAW_PAGATA_PARZIALE =
            List.of("ESEGUITA_PARZIALE", "ESEGUITO_PARZIALE", "PAGATA_PARZIALE", "PAGATO_PARZIALE", "PARZIALMENTE_ESEGUITO");
    private static final List<String> RAW_RICONCILIATA =
            List.of("INCASSATA", "INCASSATO", "RICONCILIATA", "RICONCILIATO");
    private static final List<String> RAW_ANNULLATA = List.of("ANNULLATA", "ANNULLATO");
    private static final List<String> RAW_ANOMALA = List.of("ANOMALA", "ANOMALO");

    /**
     * Traduce lo stato V2 sul/i valore/i grezzo/i di {@code stato_versamento}.
     * V1 non e' consistente sul genere del valore grezzo (visto sia
     * {@code ESEGUITO} che {@code ESEGUITA} in dati reali): ogni stato include
     * tutte le varianti riconosciute, esattamente come gia' fa
     * {@link PendenzaMapper#mapStato} in lettura — un filtro piu' stretto
     * lascerebbe fuori righe che l'output mostra correttamente mappate.
     * {@code PAGATA} include anche gli stati interni equivalenti
     * ({@code ESEGUITO_ALTRO_CANALE}, {@code ESEGUITO_SENZA_RPT}).
     * {@code NON_PAGATA} e {@code SCADUTA} condividono lo stesso valore grezzo
     * e si distinguono solo per {@code data_scadenza} rispetto a {@code now} —
     * stessa semantica di V1 (V1 {@code PendenzeDAO}:
     * {@code AbilitaFiltroNonScaduto}/{@code AbilitaFiltroScaduto}), non un
     * {@code equal} semplice. Righe con un valore grezzo non riconosciuto (che
     * il mapper di output marca comunque {@code ANOMALA} per default) non sono
     * raggiunte da nessuno stato filtrabile: limite noto, non un requisito di
     * questa issue.
     */
    public static Specification<Versamento> statoExact(StatoPendenza stato, OffsetDateTime now) {
        if (stato == null) {
            return null;
        }
        return switch (stato) {
            case PAGATA -> (root, q, cb) -> root.<String>get("statoVersamento").in(RAW_PAGATA);
            case PAGATA_PARZIALE -> (root, q, cb) -> root.<String>get("statoVersamento").in(RAW_PAGATA_PARZIALE);
            case RICONCILIATA -> (root, q, cb) -> root.<String>get("statoVersamento").in(RAW_RICONCILIATA);
            case ANNULLATA -> (root, q, cb) -> root.<String>get("statoVersamento").in(RAW_ANNULLATA);
            case ANOMALA -> (root, q, cb) -> root.<String>get("statoVersamento").in(RAW_ANOMALA);
            case NON_PAGATA -> (root, q, cb) -> cb.and(
                    root.<String>get("statoVersamento").in(RAW_NON_ESEGUITO),
                    cb.or(cb.isNull(root.get("dataScadenza")), cb.greaterThanOrEqualTo(root.get("dataScadenza"), now)));
            case SCADUTA -> (root, q, cb) -> cb.and(
                    root.<String>get("statoVersamento").in(RAW_NON_ESEGUITO),
                    cb.isNotNull(root.get("dataScadenza")),
                    cb.lessThan(root.get("dataScadenza"), now));
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
