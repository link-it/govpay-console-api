package it.govpay.console.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.Rpt;

public interface ConsoleRptRepository extends JpaRepository<Rpt, Long>, JpaSpecificationExecutor<Rpt> {

    /**
     * RT insieme alla sua pendenza (con opzione e posizione, se presenti) e al codice
     * dell'applicazione ({@code idA2A}), usati da mapper, PDF e visibilita'. Per una RT senza
     * pendenza ({@code rpt.id_versamento} nullo) {@code pendenza} e {@code idA2A} sono nulli.
     */
    record RptConPendenza(Rpt rpt, Pendenza pendenza, String idA2A) {
    }

    /** Proiezione di {@code (id, iur)} di una RT: non legge i corpi XML. */
    interface RptIur {
        Long getId();

        String getIur();
    }

    /** {@code (id, iur)} delle RT indicate, senza caricare le entity. */
    @Query("select r.id as id, r.iur as iur from Rpt r where r.id in :ids")
    List<RptIur> findIurByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Tutte le RT di una pendenza, ordinate per {@code data_msg_ricevuta DESC}
     * (RT piu' recenti prima). Usata dalla lista {@code /ricevute} di una pendenza:
     * include ogni RT presente.
     */
    @Query("""
            select r from Rpt r
             where r.idVersamento = :idPendenza
               and r.xmlRt is not null
               and r.dataMsgRicevuta is not null
             order by r.dataMsgRicevuta desc
            """)
    List<Rpt> findByIdPendenza(@Param("idPendenza") Long idPendenza);

    /**
     * RT identificata dalla tupla {@code (idDominio, iuv, idRicevuta)} dove
     * {@code idRicevuta} è il {@code ccp} di V1. La tupla è documentata come unica;
     * per robustezza su dati storici (es. più RT legacy con {@code ccp = 'n/a'}) si
     * ordina per {@code data_msg_ricevuta DESC} e si prende la prima.
     *
     * <p>Solo righe che sono effettivamente ricevute ({@code xml_rt IS NOT NULL} e
     * {@code data_msg_ricevuta IS NOT NULL}): una {@code rpt} con sola richiesta, o
     * senza data ricevuta, non è una ricevuta → 404 sul dettaglio e sui
     * sub-resource. Garantisce inoltre che {@code Ricevuta.dataRicevuta} (required)
     * sia sempre valorizzato.
     */
    @Query("""
            select r from Rpt r
             where r.codDominio = :idDominio
               and r.iuv = :iuv
               and r.iur = :idRicevuta
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

    /**
     * RPT identificata da {@code (idDominio, iuv)}, **senza** richiedere che la
     * RT sia già acquisita: usata dal pre-flight del recupero puntuale
     * ({@code POST /ricevute/recuperi}), che cerca il pagamento
     * proprio perché la RT manca. A differenza di {@link #findByKey}, non
     * filtra su {@code xml_rt}/{@code data_msg_ricevuta}: chi chiama decide se
     * la loro presenza è un 409 (RT già acquisita) o un via libera.
     */
    @Query("""
            select r from Rpt r
             where r.codDominio = :idDominio
               and r.iuv = :iuv
             order by r.dataMsgRichiesta desc
            """)
    List<Rpt> findByDominioAndIuv(@Param("idDominio") String idDominio,
                                  @Param("iuv") String iuv,
                                  Pageable pageable);

    default Optional<Rpt> findByDominioAndIuv(String idDominio, String iuv) {
        List<Rpt> hits = findByDominioAndIuv(idDominio, iuv, PageRequest.of(0, 1));
        return hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
    }

    /**
     * Come {@link #findByKey(String, String, String)}, ma restituisce anche la pendenza della
     * ricevuta, con opzione e posizione se presenti, e il codice dell'applicazione, in un'unica
     * query. Include le ricevute senza pendenza; esclude quelle il cui {@code id_versamento}
     * non porta a una pendenza con la sua applicazione.
     */
    @Query("""
            select r, p, a.codApplicazione from Rpt r
              left join Pendenza p on p.id = r.idVersamento
              left join Applicazione a on a.id = p.idApplicazione
              left join fetch p.opzionePagamento o
              left join fetch o.pdDebitore d
              left join fetch d.posizioneDebitoria
             where (r.idVersamento is null or a.id is not null)
               and r.codDominio = :idDominio
               and r.iuv = :iuv
               and r.iur = :idRicevuta
               and r.xmlRt is not null
               and r.dataMsgRicevuta is not null
             order by r.dataMsgRicevuta desc
            """)
    List<Object[]> findConPendenzaByKey(@Param("idDominio") String idDominio,
                                        @Param("iuv") String iuv,
                                        @Param("idRicevuta") String idRicevuta,
                                        Pageable pageable);

    default Optional<RptConPendenza> findConPendenzaByKey(String idDominio, String iuv, String idRicevuta) {
        return findConPendenzaByKey(idDominio, iuv, idRicevuta, PageRequest.of(0, 1)).stream()
                .findFirst().map(ConsoleRptRepository::conPendenza);
    }

    /**
     * Come {@link #findByDominioAndIuv(String, String)}, ma restituisce anche la pendenza e
     * il codice dell'applicazione in un'unica query.
     */
    @Query("""
            select r, p, a.codApplicazione from Rpt r
              left join Pendenza p on p.id = r.idVersamento
              left join Applicazione a on a.id = p.idApplicazione
              left join fetch p.opzionePagamento o
              left join fetch o.pdDebitore d
              left join fetch d.posizioneDebitoria
             where (r.idVersamento is null or a.id is not null)
               and r.codDominio = :idDominio
               and r.iuv = :iuv
             order by r.dataMsgRichiesta desc
            """)
    List<Object[]> findConPendenzaByDominioAndIuv(@Param("idDominio") String idDominio,
                                                  @Param("iuv") String iuv,
                                                  Pageable pageable);

    default Optional<RptConPendenza> findConPendenzaByDominioAndIuv(String idDominio, String iuv) {
        return findConPendenzaByDominioAndIuv(idDominio, iuv, PageRequest.of(0, 1)).stream()
                .findFirst().map(ConsoleRptRepository::conPendenza);
    }

    private static RptConPendenza conPendenza(Object[] riga) {
        return new RptConPendenza((Rpt) riga[0], (Pendenza) riga[1], (String) riga[2]);
    }
}
