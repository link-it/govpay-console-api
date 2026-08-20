package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.govpay.console.entity.Rendicontazione;
import it.govpay.console.rendicontazione.FrPeriodo;

public interface RendicontazioneRepository extends JpaRepository<Rendicontazione, Long> {

    /**
     * Finestra temporale coperta dal flusso, per il dettaglio (mai nella lista:
     * costo aggiuntivo di un {@code MIN}/{@code MAX} con join implicito).
     * Nessuna riga di rendicontazione collegata → entrambi i campi {@code null}.
     */
    @Query("""
            select new it.govpay.console.rendicontazione.FrPeriodo(min(r.data), max(r.data))
              from Rendicontazione r
             where r.idFr = :idFr
            """)
    FrPeriodo findPeriodo(@Param("idFr") Long idFr);
}
