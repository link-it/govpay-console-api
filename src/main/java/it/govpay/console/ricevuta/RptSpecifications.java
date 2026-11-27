package it.govpay.console.ricevuta;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.query.criteria.JpaEntityJoin;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.sqm.tree.SqmJoinType;
import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.common.GiornoDatabase;
import it.govpay.console.common.LikePatterns;
import it.govpay.console.common.PendenzaPredicates;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.PendenzaUoStorica;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.Rpt;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.PendenzaVisibilita;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Root;

/**
 * Predicati di ricerca per la collection {@code GET /ricevute} (entità {@link Rpt}). I filtri su
 * colonne di {@code rpt} sono locali a questa classe; quelli raggiungibili via pendenza
 * ({@code rpt.id_versamento}) delegano a {@link PendenzaPredicates} quando la semantica coincide
 * con quella di {@code /pendenze}: non tutti, {@code idPendenza} resta locale perché qui è a match
 * esatto, mentre su {@code /pendenze} è a match parziale. La visibilità ACL viene spinta nella
 * query lungo lo stesso join.
 *
 * <p>La ricevuta e' un pagamento gia' avvenuto: i join verso l'opzione di pagamento, il debitore
 * e la posizione sono esterni, cosi' una ricevuta la cui pendenza non ha opzione (dati storici non
 * migrati) resta ricercabile; i filtri sul debitore non la trovano. La unita' operativa e' quella
 * della posizione e, per una pendenza senza opzione, quella storica ({@link PendenzaUoStorica}):
 * assente vale come quella {@code EC} del dominio.
 *
 * <p>Una ricevuta senza pendenza ({@code rpt.id_versamento} nullo) resta ricercabile sui dati
 * della {@code rpt} e per {@code idA2A}, tramite {@code rpt.id_applicazione}; i filtri sui dati
 * della pendenza, del debitore e della unita' operativa non la trovano. Una ricevuta il cui
 * {@code id_versamento} non porta a una pendenza e' esclusa.
 */
public final class RptSpecifications {

    private static final String FIELD_DATA_MSG_RICEVUTA = "dataMsgRicevuta";
    private static final String FIELD_DATA_MSG_RICHIESTA = "dataMsgRichiesta";
    /** Codice della unita' operativa che rappresenta l'ente creditore stesso. */
    private static final String COD_UNITA_OPERATIVA_EC = "EC";

    private RptSpecifications() {
    }

    /**
     * Join sulla {@code Pendenza} della ricevuta ({@code rpt.id_versamento}, FK piatta: la
     * {@code Rpt} della libreria non ha l'associazione). Il join e' creato una sola volta per
     * query e riusato da tutti i predicati, cosi' filtri e visibilita' lavorano sulla stessa riga.
     */
    @SuppressWarnings("unchecked")
    private static From<?, Pendenza> pendenza(Root<Rpt> root, CriteriaBuilder cb) {
        for (Join<Rpt, ?> join : root.getJoins()) {
            if (Pendenza.class.equals(join.getJavaType())) {
                return (From<?, Pendenza>) join;
            }
        }
        JpaEntityJoin<Rpt, Pendenza> join = ((JpaRoot<Rpt>) root).join(Pendenza.class, SqmJoinType.LEFT);
        join.on(cb.equal(join.get("id"), root.get("idVersamento")));
        return join;
    }

    /** Unita' operativa storica della pendenza ({@code versamenti.id_uo}), stesso join per query. */
    @SuppressWarnings("unchecked")
    private static From<?, PendenzaUoStorica> storica(Root<Rpt> root, CriteriaBuilder cb) {
        for (Join<Rpt, ?> join : root.getJoins()) {
            if (PendenzaUoStorica.class.equals(join.getJavaType())) {
                return (From<?, PendenzaUoStorica>) join;
            }
        }
        JpaEntityJoin<Rpt, PendenzaUoStorica> join = ((JpaRoot<Rpt>) root).join(PendenzaUoStorica.class,
                SqmJoinType.LEFT);
        join.on(cb.equal(join.get("id"), root.get("idVersamento")));
        return join;
    }

    /**
     * Unita' operativa della pendenza: quella della posizione se la pendenza ha un'opzione di
     * pagamento, altrimenti quella storica.
     */
    private static Expression<Long> uoEffettiva(Root<Rpt> root, CriteriaBuilder cb) {
        Join<?, ?> opzione = sinistro(pendenza(root, cb), "opzionePagamento");
        return cb.<Long>selectCase()
                .when(cb.isNull(opzione.get("id")), storica(root, cb).<Long>get("idUo"))
                .otherwise(posizione(root, cb).<Long>get("idUnitaOperativa"));
    }

    /** Join esterno su {@code attributo}, riusato se la query lo ha gia' (un solo join per percorso). */
    private static Join<?, ?> sinistro(From<?, ?> from, String attributo) {
        for (Join<?, ?> esistente : from.getJoins()) {
            if (esistente.getAttribute().getName().equals(attributo) && esistente.getJoinType() == JoinType.LEFT) {
                return esistente;
            }
        }
        return from.join(attributo, JoinType.LEFT);
    }

    private static Join<?, ?> pdDebitore(Root<Rpt> root, CriteriaBuilder cb) {
        return sinistro(sinistro(pendenza(root, cb), "opzionePagamento"), "pdDebitore");
    }

    private static Join<?, ?> posizione(Root<Rpt> root, CriteriaBuilder cb) {
        return sinistro(pdDebitore(root, cb), "posizioneDebitoria");
    }

    private static Join<?, ?> debitore(Root<Rpt> root, CriteriaBuilder cb) {
        return sinistro(pdDebitore(root, cb), "debitore");
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
        return (root, q, cb) -> cb.equal(root.get("iur"), value);
    }

    /** Limite inferiore incluso sulla data di ricezione della ricevuta ({@code data_msg_ricevuta}). */
    public static Specification<Rpt> dataRicevutaDa(LocalDate da) {
        if (da == null) {
            return null;
        }
        OffsetDateTime from = GiornoDatabase.inizio(da);
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get(FIELD_DATA_MSG_RICEVUTA), from);
    }

    /** Limite superiore incluso: {@code data_msg_ricevuta < (dataRicevutaA + 1 giorno)}. */
    public static Specification<Rpt> dataRicevutaA(LocalDate a) {
        if (a == null) {
            return null;
        }
        OffsetDateTime toExclusive = GiornoDatabase.inizio(a.plusDays(1));
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
        OffsetDateTime from = GiornoDatabase.inizio(da);
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get(FIELD_DATA_MSG_RICHIESTA), from);
    }

    /** Limite superiore incluso: {@code data_msg_richiesta < (dataRichiestaA + 1 giorno)}. */
    public static Specification<Rpt> dataRichiestaA(LocalDate a) {
        if (a == null) {
            return null;
        }
        OffsetDateTime toExclusive = GiornoDatabase.inizio(a.plusDays(1));
        return (root, q, cb) -> cb.lessThan(root.get(FIELD_DATA_MSG_RICHIESTA), toExclusive);
    }

    public static Specification<Rpt> idA2AExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> {
            Subquery<Long> sub = q.subquery(Long.class);
            Root<Applicazione> applicazione = sub.from(Applicazione.class);
            sub.select(applicazione.get("id")).where(cb.equal(applicazione.get("codApplicazione"), value));
            return cb.or(PendenzaPredicates.idA2AExact(cb, q, pendenza(root, cb), value),
                    cb.and(senzaPendenza(root, cb), root.<Long>get("idApplicazione").in(sub)));
        };
    }

    /**
     * Match esatto su {@code idPendenza}. Non delega a
     * {@code PendenzaSpecifications.idPendenzaPartial}: qui si vuole
     * match esatto (fedele a V1, dove sia {@code /pendenze} sia {@code /rpp}
     * usano {@code equals}), mentre {@code /pendenze} in V2 usa un match
     * parziale per UX di ricerca, non toccata qui.
     */
    public static Specification<Rpt> idPendenzaExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(pendenza(root, cb).get("idPendenza"), value);
    }

    public static Specification<Rpt> identificativoDebitoreExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> PendenzaPredicates.identificativoDebitoreExact(cb, debitore(root, cb), value);
    }

    public static Specification<Rpt> idUnitaOperativaExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> {
            Subquery<Long> sub = q.subquery(Long.class);
            Root<UnitaOperativa> uo = sub.from(UnitaOperativa.class);
            sub.select(uo.get("id")).where(cb.equal(uo.get("codUo"), value));
            Expression<Long> idUo = uoEffettiva(root, cb);
            Predicate perCodice = idUo.in(sub);
            // Senza unita' operativa la pendenza vale come quella EC; una ricevuta senza
            // pendenza non ha unita' operativa e nessun valore del filtro la trova.
            return COD_UNITA_OPERATIVA_EC.equals(value)
                    ? cb.and(conPendenza(root, cb), cb.or(perCodice, cb.isNull(idUo)))
                    : perCodice;
        };
    }

    public static Specification<Rpt> idTipoPendenzaIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> PendenzaPredicates.idTipoPendenzaIn(cb, q, pendenza(root, cb), values);
    }

    public static Specification<Rpt> direzioneIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> PendenzaPredicates.direzioneIn(pendenza(root, cb), values);
    }

    public static Specification<Rpt> divisioneIn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return (root, q, cb) -> PendenzaPredicates.divisioneIn(pendenza(root, cb), values);
    }

    public static Specification<Rpt> tassonomiaExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(pendenza(root, cb).get("tassonomia"), value);
    }

    /**
     * Ricerca testuale (contains, case-insensitive) su nome/ragione sociale del
     * debitore dell'opzione di pagamento della pendenza. Non condivisa con
     * {@code PendenzaPredicates}: nessuna risorsa esistente ha un filtro equivalente da riusare. Il chiamante valida la lunghezza minima del
     * termine (anti-enumerazione) prima di applicare il predicato:
     * qui si assume già verificata.
     *
     * <p>{@code %} e {@code _} nel termine cercato sono escaped prima di essere
     * racchiusi fra i due {@code %} che delimitano il "contains": senza
     * escaping sarebbero wildcard SQL, non caratteri letterali, e un termine
     * come {@code ___} (3 caratteri, supera la soglia minima) matcherebbe
     * quasi tutto l'archivio invece di essere un contains letterale su
     * "___" — esattamente la ricerca-per-persona che si vuole mirata.
     */
    public static Specification<Rpt> anagraficaDebitorePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + LikePatterns.escape(value.toLowerCase()) + "%";
        return (root, q, cb) -> cb.like(cb.lower(debitore(root, cb).<String>get("anagrafica")), pattern, LikePatterns.ESCAPE_CHAR);
    }

    /**
     * Limita le ricevute a quelle visibili all'operatore, con le regole di
     * {@link PendenzaVisibilita}: quelle con pendenza secondo la visibilita' della pendenza,
     * quelle senza pendenza secondo il solo dominio della {@code rpt} (vedi
     * {@link RicevutaVisibilita}). Va sempre applicata: esclude anche le ricevute il cui
     * {@code id_versamento} non porta a una pendenza.
     *
     * @param dominiConUoEcVisibile domini di cui l'operatore ha la unita' operativa {@code EC}
     */
    public static Specification<Rpt> visibiliPerOperatore(OperatoreCorrente operatore,
                                                          Set<Long> dominiConUoEcVisibile) {
        return (root, q, cb) -> {
            Predicate diPendenzaVisibile = cb.and(conPendenza(root, cb),
                    PendenzaVisibilita.predicate(cb,
                            pendenza(root, cb).<Long>get("idDominio"),
                            uoEffettiva(root, cb),
                            pendenza(root, cb).<Long>get("idTipoVersamento"),
                            dominiConUoEcVisibile, operatore));
            return cb.or(diPendenzaVisibile,
                    senzaPendenzaVisibile(root, q, cb, operatore, dominiConUoEcVisibile));
        };
    }

    /** La ricevuta ha una pendenza, ed esiste. */
    private static Predicate conPendenza(Root<Rpt> root, CriteriaBuilder cb) {
        return cb.isNotNull(pendenza(root, cb).get("id"));
    }

    private static Predicate senzaPendenza(Root<Rpt> root, CriteriaBuilder cb) {
        return cb.isNull(root.get("idVersamento"));
    }

    private static Predicate senzaPendenzaVisibile(Root<Rpt> root, CriteriaQuery<?> q, CriteriaBuilder cb,
                                                   OperatoreCorrente operatore,
                                                   Set<Long> dominiConUoEcVisibile) {
        if (operatore.tuttiIDomini()) {
            return senzaPendenza(root, cb);
        }
        Set<Long> domini = PendenzaVisibilita.dominiVisibiliSenzaUnitaOperativa(dominiConUoEcVisibile, operatore);
        if (domini.isEmpty()) {
            return cb.disjunction();
        }
        Subquery<String> codici = q.subquery(String.class);
        Root<Dominio> dominio = codici.from(Dominio.class);
        codici.select(dominio.get("codDominio")).where(dominio.get("id").in(domini));
        return cb.and(senzaPendenza(root, cb), root.get("codDominio").in(codici));
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
