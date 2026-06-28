package it.govpay.console.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Dominio;

public interface DominioRepository
        extends JpaRepository<Dominio, Long>, JpaSpecificationExecutor<Dominio> {

    List<Dominio> findByStazione_IdOrderByCodDominioAsc(Long idStazione);

    Optional<Dominio> findByCodDominio(String codDominio);

    boolean existsByCodDominio(String codDominio);
}
