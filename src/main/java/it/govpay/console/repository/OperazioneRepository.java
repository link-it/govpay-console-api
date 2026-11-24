package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Operazione;

public interface OperazioneRepository extends JpaRepository<Operazione, Long>, JpaSpecificationExecutor<Operazione> {

    /**
     * L'operazione con la sua pendenza e il debitore dell'opzione di pagamento (se presente),
     * in un'unica query: servono al soggetto pagatore del dettaglio.
     */
    @EntityGraph(attributePaths = {"pendenza", "pendenza.opzionePagamento", "pendenza.opzionePagamento.pdDebitore",
            "pendenza.opzionePagamento.pdDebitore.debitore"})
    Optional<Operazione> findByTracciato_IdAndLineaElaborazione(Long idTracciato, Long lineaElaborazione);
}
