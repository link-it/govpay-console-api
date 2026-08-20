package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Fr;

public interface FrRepository extends JpaRepository<Fr, Long>, JpaSpecificationExecutor<Fr> {

    /** Chiave canonica del dettaglio: coincide col vincolo {@code unique_fr_2} già presente a DB. */
    Optional<Fr> findByCodDominioAndCodFlussoAndCodPspAndRevisione(
            String codDominio, String codFlusso, String codPsp, Long revisione);
}
