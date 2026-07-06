package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Applicazione;

public interface ApplicazioneRepository
        extends JpaRepository<Applicazione, Long>, JpaSpecificationExecutor<Applicazione> {

    Optional<Applicazione> findByCodApplicazione(String codApplicazione);

    boolean existsByCodApplicazione(String codApplicazione);
}
