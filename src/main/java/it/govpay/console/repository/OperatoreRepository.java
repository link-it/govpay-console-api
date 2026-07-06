package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Operatore;

public interface OperatoreRepository
        extends JpaRepository<Operatore, Long>, JpaSpecificationExecutor<Operatore> {

    Optional<Operatore> findByIdUtenza(Long idUtenza);

    Optional<Operatore> findByUtenza_PrincipalOriginale(String principalOriginale);
}
