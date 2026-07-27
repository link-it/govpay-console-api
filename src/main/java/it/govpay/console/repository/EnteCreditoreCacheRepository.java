package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.EnteCreditoreCache;

public interface EnteCreditoreCacheRepository
        extends JpaRepository<EnteCreditoreCache, Long>, JpaSpecificationExecutor<EnteCreditoreCache> {

    Optional<EnteCreditoreCache> findByCodFiscale(String codFiscale);
}
