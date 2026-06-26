package it.govpay.console.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.govpay.console.entity.Rpt;

public interface RptRepository extends JpaRepository<Rpt, Long>, JpaSpecificationExecutor<Rpt> {

    /**
     * "RT principale" per una pendenza: l'ultima RT con esito {@code Eseguito} o
     * {@code Parzialmente eseguito} (coerente con scope C della issue #9: il
     * link {@code _links.ricevuta} appare solo quando la pendenza e' in stato
     * {@code PAGATA | PAGATA_PARZIALE | RICONCILIATA}). Ordina per
     * {@code data_msg_ricevuta DESC} e prende la prima.
     *
     * <p>L'EntityGraph carica {@code versamento.dominio} e
     * {@code versamento.singoliVersamenti} che servono al mapper di Ricevuta
     * e al payload PDF.
     */
    @EntityGraph(attributePaths = {"versamento", "versamento.dominio",
            "versamento.singoliVersamenti"})
    @Query("""
            select r from Rpt r
             where r.versamento.applicazione.codApplicazione = :idA2A
               and r.versamento.codVersamentoEnte = :idPendenza
               and r.xmlRt is not null
               and r.codEsitoPagamento in (0, 2)
             order by r.dataMsgRicevuta desc
            """)
    List<Rpt> findPrincipale(@Param("idA2A") String idA2A,
                             @Param("idPendenza") String idPendenza,
                             Pageable pageable);

    default Optional<Rpt> findPrincipale(String idA2A, String idPendenza) {
        List<Rpt> hits = findPrincipale(idA2A, idPendenza, PageRequest.of(0, 1));
        return hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
    }

    /**
     * Tutte le RT di una pendenza, ordinate per {@code data_msg_ricevuta DESC}
     * (RT piu' recenti prima). Usata dalla lista {@code /ricevute}: include ogni
     * RT presente, non solo la principale.
     */
    @EntityGraph(attributePaths = {"versamento", "versamento.dominio"})
    @Query("""
            select r from Rpt r
             where r.versamento.applicazione.codApplicazione = :idA2A
               and r.versamento.codVersamentoEnte = :idPendenza
               and r.xmlRt is not null
               and r.dataMsgRicevuta is not null
             order by r.dataMsgRicevuta desc
            """)
    List<Rpt> findByPendenza(@Param("idA2A") String idA2A, @Param("idPendenza") String idPendenza);

    /**
     * RT identificata dalla tupla {@code (idDominio, iuv, idRicevuta)} dove
     * {@code idRicevuta} è il {@code ccp} di V1. La tupla è documentata come unica;
     * per robustezza su dati storici (es. più RT legacy con {@code ccp = 'n/a'}) si
     * ordina per {@code data_msg_ricevuta DESC} e si prende la prima.
     *
     * <p>Solo righe che sono effettivamente ricevute ({@code xml_rt IS NOT NULL} e
     * {@code data_msg_ricevuta IS NOT NULL}): una {@code rpt} con sola richiesta, o
     * senza data pagamento, non è una ricevuta → 404 sul dettaglio e sui
     * sub-resource. Garantisce inoltre che {@code Ricevuta.dataPagamento} (required)
     * sia sempre valorizzato.
     *
     * <p>L'EntityGraph carica le associazioni del {@code versamento} usate dal
     * mapper di dettaglio (pendenza ref, _links) e dal check di visibilità ACL.
     */
    @EntityGraph(attributePaths = {"versamento", "versamento.applicazione", "versamento.dominio",
            "versamento.unitaOperativa", "versamento.tipoVersamento"})
    @Query("""
            select r from Rpt r
             where r.codDominio = :idDominio
               and r.iuv = :iuv
               and r.ccp = :idRicevuta
               and r.xmlRt is not null
               and r.dataMsgRicevuta is not null
             order by r.dataMsgRicevuta desc
            """)
    List<Rpt> findByKey(@Param("idDominio") String idDominio,
                        @Param("iuv") String iuv,
                        @Param("idRicevuta") String idRicevuta,
                        Pageable pageable);

    default Optional<Rpt> findByKey(String idDominio, String iuv, String idRicevuta) {
        List<Rpt> hits = findByKey(idDominio, iuv, idRicevuta, PageRequest.of(0, 1));
        return hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
    }
}
