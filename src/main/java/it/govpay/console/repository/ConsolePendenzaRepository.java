package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.govpay.pendenze.entity.Pendenza;

/** Letture della pendenza proprie della console, che non richiedono l'opzione di pagamento. */
public interface ConsolePendenzaRepository extends JpaRepository<Pendenza, Long> {

    /**
     * La pendenza per {@code (idA2A, idPendenza)} con, se c'e', l'opzione di pagamento e la
     * posizione: a differenza della lettura del dettaglio non esclude le pendenze senza
     * opzione, di cui restano le ricevute.
     */
    @Query("""
            select p from Pendenza p
              left join fetch p.opzionePagamento o
              left join fetch o.pdDebitore d
              left join fetch d.posizioneDebitoria
             where p.idApplicazione in (select a.id from Applicazione a where a.codApplicazione = :idA2A)
               and p.idPendenza = :idPendenza
            """)
    Optional<Pendenza> findPerIdentificativo(@Param("idA2A") String idA2A, @Param("idPendenza") String idPendenza);
}
