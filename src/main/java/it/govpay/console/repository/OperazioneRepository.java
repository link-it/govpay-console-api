package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Operazione;

public interface OperazioneRepository extends JpaRepository<Operazione, Long>, JpaSpecificationExecutor<Operazione> {

    Optional<Operazione> findByTracciato_IdAndLineaElaborazione(Long idTracciato, Long lineaElaborazione);
}
