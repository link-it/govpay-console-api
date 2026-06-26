package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Intermediario;

public interface IntermediarioRepository
        extends JpaRepository<Intermediario, Long>, JpaSpecificationExecutor<Intermediario> {

    Optional<Intermediario> findByCodIntermediario(String codIntermediario);

    boolean existsByCodIntermediario(String codIntermediario);
}
