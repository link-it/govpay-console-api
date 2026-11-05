package it.govpay.console.repository;

import java.util.List;

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

    /**
     * Righe di rendicontazione correnti (flusso non obsoleto) dei pagamenti
     * indicati. Puo' restituire piu' righe per lo stesso {@code idPagamento}.
     */
    @Query("""
            select r from Rendicontazione r
             where r.idPagamento in :idPagamenti and r.fr.obsoleto = false
            """)
    List<Rendicontazione> findCorrentiByIdPagamentoIn(@Param("idPagamenti") List<Long> idPagamenti);
}
