package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.TipoTributo;

public interface TipoTributoRepository
        extends JpaRepository<TipoTributo, Long>, JpaSpecificationExecutor<TipoTributo> {

    Optional<TipoTributo> findByCodTributo(String codTributo);

    boolean existsByCodTributo(String codTributo);
}
