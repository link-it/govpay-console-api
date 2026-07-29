package it.govpay.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.IbanCache;

public interface IbanCacheRepository extends JpaRepository<IbanCache, Long> {

    List<IbanCache> findByCodDominioOrderByIbanAsc(String codDominio);
}
