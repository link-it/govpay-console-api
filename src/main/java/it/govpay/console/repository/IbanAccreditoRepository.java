package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.IbanAccredito;

public interface IbanAccreditoRepository
        extends JpaRepository<IbanAccredito, Long>, JpaSpecificationExecutor<IbanAccredito> {

    Optional<IbanAccredito> findByDominio_IdAndCodIban(Long idDominio, String codIban);

    boolean existsByDominio_IdAndCodIban(Long idDominio, String codIban);
}
