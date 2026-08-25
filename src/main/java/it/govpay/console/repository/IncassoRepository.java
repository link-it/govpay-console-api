package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Incasso;

public interface IncassoRepository extends JpaRepository<Incasso, Long>, JpaSpecificationExecutor<Incasso> {

    /** Chiave canonica del dettaglio: coincide col vincolo {@code unique_incassi_1} già presente a DB. */
    Optional<Incasso> findByCodDominioAndIdentificativo(String codDominio, String identificativo);
}
