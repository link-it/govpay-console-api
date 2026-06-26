package it.govpay.console.ricevuta;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Rpt;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.VersamentoVisibilita;

/**
 * Predicati di ricerca per la collection {@code GET /ricevute} (entità
 * {@link Rpt}). I 5 filtri di Fase 1 sono match esatti su colonne della tabella
 * {@code rpt}; la visibilità ACL viene spinta nella query navigando il
 * {@code versamento} associato.
 */
public final class RptSpecifications {

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

    /** Limite inferiore incluso sulla data di pagamento ({@code data_msg_ricevuta}). */
    public static Specification<Rpt> dataPagamentoDa(LocalDate da) {
        if (da == null) {
            return null;
        }
        OffsetDateTime from = da.atStartOfDay().atOffset(ZoneOffset.UTC);
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("dataMsgRicevuta"), from);
    }

    /** Limite superiore incluso: {@code data_msg_ricevuta < (dataA + 1 giorno)}. */
    public static Specification<Rpt> dataPagamentoA(LocalDate a) {
        if (a == null) {
            return null;
        }
        OffsetDateTime toExclusive = a.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return (root, q, cb) -> cb.lessThan(root.get("dataMsgRicevuta"), toExclusive);
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
     * pagina (cursor keyset) proprio su {@code dataPagamento}. Senza questo vincolo
     * una riga con data nulla romperebbe l'ordinamento e la codifica del cursore.
     */
    public static Specification<Rpt> conRicevuta() {
        return (root, q, cb) -> cb.and(
                cb.isNotNull(root.get("xmlRt")),
                cb.isNotNull(root.get("dataMsgRicevuta")));
    }
}
