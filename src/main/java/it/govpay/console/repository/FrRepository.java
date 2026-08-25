package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Fr;

public interface FrRepository extends JpaRepository<Fr, Long>, JpaSpecificationExecutor<Fr> {

    /** Chiave canonica del dettaglio: coincide col vincolo {@code unique_fr_2} già presente a DB. */
    Optional<Fr> findByCodDominioAndCodFlussoAndCodPspAndRevisione(
            String codDominio, String codFlusso, String codPsp, Long revisione);

    /**
     * Il flusso "corrente" per riversamento cumulativo (controlli pre-flight
     * §9-§12 della registrazione riconciliazione): un solo PSP/revisione non
     * obsoleta per {@code (codDominio, codFlusso)}, stessa assunzione di
     * V1 ({@code FrBD.getFr(dominio, idf, null, false)}, che non gestisce
     * risultati multipli).
     */
    Optional<Fr> findByCodDominioAndCodFlussoAndObsoletoFalse(String codDominio, String codFlusso);
}
