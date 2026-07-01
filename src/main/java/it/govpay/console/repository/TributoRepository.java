package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Tributo;

public interface TributoRepository
        extends JpaRepository<Tributo, Long>, JpaSpecificationExecutor<Tributo> {

    Optional<Tributo> findByDominio_IdAndTipoTributo_CodTributo(Long idDominio, String codTributo);

    boolean existsByDominio_IdAndTipoTributo_CodTributo(Long idDominio, String codTributo);
}
