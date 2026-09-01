package it.govpay.console.ricevuta;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.LikePatterns;
import it.govpay.console.common.VersamentoPredicates;
import it.govpay.console.entity.Rpt;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.VersamentoVisibilita;

/**
 * Predicati di ricerca per la collection {@code GET /ricevute} (entità
 * {@link Rpt}). I filtri su colonne di {@code rpt} sono locali a questa
 * classe; quelli raggiungibili via {@code versamento} (issue #68 §A) delegano
 * a {@link VersamentoPredicates} quando la semantica coincide con quella di
 * {@code /pendenze} — non tutti: {@code idPendenza} resta locale perché qui è
 * a match esatto, mentre su {@code /pendenze} è a match parziale (stessa
 * colonna, semantica diversa, vedi {@link VersamentoPredicates}). La
 * visibilità ACL viene spinta nella query navigando il {@code versamento}
 * associato.
 *
 * <p><b>Verifica indici (issue #68 §A)</b>, confermata con
 * {@code EXPLAIN (ANALYZE, BUFFERS)} su dataset sintetico (50.000
 * {@code versamenti}/{@code rpt}, schema V1 reale incluso ogni indice
 * esistente): {@code idPendenzaExact} e {@code identificativoDebitoreExact}
 * usano indici dedicati già esistenti ({@code idx_vrs_id_pendenza},
 * {@code idx_vrs_deb_identificativo}), sub-millisecondo. {@code idA2AExact},
 * {@code idTipoPendenzaIn}, {@code direzioneIn}, {@code divisioneIn} e
 * {@code tassonomiaExact} producono una {@code Seq Scan} su {@code versamenti}
 * (nessun indice leading su queste colonne) — confermando le lacune già
 * proposte-non-applicate nei Javadoc di {@code PendenzaSpecifications} (issue
 * #66) — ma restano comunque sotto i 7ms a questo volume, e nella
 * combinazione realistica con {@code idDominio} (quasi sempre presente
 * nell'uso reale) tutte usano {@code idx_vrs_auth} invece della scan. Nessun
 * nuovo indice applicato.
 */
public final class RptSpecifications {

    private static final String FIELD_DATA_MSG_RICEVUTA = "dataMsgRicevuta";
    private static final String FIELD_DATA_MSG_RICHIESTA = "dataMsgRichiesta";

    private RptSpecifications() {
    }

    public static Specification<Rpt> iuvExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("iuv"), value);
    }

    public static Specification<Rpt> idDominioExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("codDominio"), value);
    }

    public static Specification<Rpt> idRicevutaExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("ccp"), value);
    }

    /** Limite inferiore incluso sulla data di ricezione della ricevuta ({@code data_msg_ricevuta}). */
    public static Specification<Rpt> dataRicevutaDa(LocalDate da) {
        if (da == null) {
            return null;
        }
        OffsetDateTime from = da.atStartOfDay().atOffset(ZoneOffset.UTC);
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get(FIELD_DATA_MSG_RICEVUTA), from);
    }

    /** Limite superiore incluso: {@code data_msg_ricevuta < (dataRicevutaA + 1 giorno)}. */
    public static Specification<Rpt> dataRicevutaA(LocalDate a) {
        if (a == null) {
            return null;
        }
        OffsetDateTime toExclusive = a.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return (root, q, cb) -> cb.lessThan(root.get(FIELD_DATA_MSG_RICEVUTA), toExclusive);
    }

    /**
     * Limite inferiore incluso sulla data della richiesta di pagamento
     * ({@code data_msg_richiesta}), indipendente da {@link #dataRicevutaDa}: RPT e
     * RT hanno colonne date separate, i due intervalli si combinano in AND senza
     * vincoli reciproci.
     */
    public static Specification<Rpt> dataRichiestaDa(LocalDate da) {
        if (da == null) {
            return null;
        }
        OffsetDateTime from = da.atStartOfDay().atOffset(ZoneOffset.UTC);
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get(FIELD_DATA_MSG_RICHIESTA), from);
    }

    /** Limite superiore incluso: {@code data_msg_richiesta < (dataRichiestaA + 1 giorno)}. */
    public static Specification<Rpt> dataRichiestaA(LocalDate a) {
        if (a == null) {
            return null;
        }
        OffsetDateTime toExclusive = a.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return (root, q, cb) -> cb.lessThan(root.get(FIELD_DATA_MSG_RICHIESTA), toExclusive);
    }

    public static Specification<Rpt> idA2AExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.idA2AExact(cb, root.get("versamento"), value);
    }

    /**
     * Match esatto su {@code versamento.codVersamentoEnte}. Non delega a
     * {@code PendenzaSpecifications.idPendenzaPartial}: qui la issue #68 vuole
     * match esatto (fedele a V1, dove sia {@code /pendenze} sia {@code /rpp}
     * usano {@code equals}), mentre {@code /pendenze} in V2 usa un match
     * parziale per UX di ricerca — scelta di #76, non toccata qui.
     */
    public static Specification<Rpt> idPendenzaExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("versamento").get("codVersamentoEnte"), value);
    }

    public static Specification<Rpt> identificativoDebitoreExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.identificativoDebitoreExact(cb, root.get("versamento"), value);
    }

    public static Specification<Rpt> idUnitaOperativaExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("versamento").get("unitaOperativa").get("codUo"), value);
    }

    public static Specification<Rpt> idTipoPendenzaIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.idTipoPendenzaIn(cb, root.get("versamento"), values);
    }

    public static Specification<Rpt> direzioneIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.direzioneIn(cb, root.get("versamento"), values);
    }

    public static Specification<Rpt> divisioneIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> VersamentoPredicates.divisioneIn(cb, root.get("versamento"), values);
    }

    public static Specification<Rpt> tassonomiaExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("versamento").get("tassonomia"), value);
    }

    /**
     * Ricerca testuale (contains, case-insensitive) su nome/ragione sociale del
     * debitore ({@code versamento.debitoreAnagrafica}, V1: {@code RptFilter.anagraficaDebitore}
     * → {@code ilike '%value%'} su {@code debitore_anagrafica}). Non condivisa con
     * {@code VersamentoPredicates}: nessuna risorsa esistente ha un filtro
     * equivalente da riusare. Il chiamante valida la lunghezza minima del
     * termine (issue #68 §C, anti-enumerazione) prima di applicare il predicato:
     * qui si assume già verificata.
     *
     * <p>{@code %} e {@code _} nel termine cercato sono escaped prima di essere
     * racchiusi fra i due {@code %} che delimitano il "contains": senza
     * escaping sarebbero wildcard SQL, non caratteri letterali, e un termine
     * come {@code ___} (3 caratteri, supera la soglia minima) matcherebbe
     * quasi tutto l'archivio invece di essere un contains letterale su
     * "___" — esattamente la ricerca-per-persona che l'issue vuole mirata.
     */
    public static Specification<Rpt> anagraficaDebitorePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("versamento").get("debitoreAnagrafica")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    public static Specification<Rpt> visibiliPerOperatore(OperatoreCorrente operatore) {
        return (root, q, cb) -> VersamentoVisibilita.predicate(cb, root.get("versamento"), operatore);
    }

    /**
     * Vincolo di dominio della collection {@code /ricevute}: la riga {@code rpt} è
     * una ricevuta solo se la RT è effettivamente presente. Allineato al filtro V1
     * "ricerco solo rpt con ricevuta" ({@code RptFilter}: {@code cod_msg_ricevuta IS
     * NOT NULL}); qui usiamo {@code xml_rt IS NOT NULL}, più stretto, per garantire
     * che {@code Ricevuta.rt} sia convertibile. Esclude le {@code rpt} con sola
     * richiesta / pagamento non concluso.
     *
     * <p>Si richiede inoltre {@code data_msg_ricevuta IS NOT NULL}: la colonna è
     * nullable a DB e non vincolata a {@code xml_rt}, mentre la collection ordina e
     * pagina (cursor keyset) proprio su {@code dataRicevuta}. Senza questo vincolo
     * una riga con data nulla romperebbe l'ordinamento e la codifica del cursore.
     */
    public static Specification<Rpt> conRicevuta() {
        return (root, q, cb) -> cb.and(
                cb.isNotNull(root.get("xmlRt")),
                cb.isNotNull(root.get(FIELD_DATA_MSG_RICEVUTA)));
    }
}
