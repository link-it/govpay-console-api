package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.JppaConfig;

public interface JppaConfigRepository extends JpaRepository<JppaConfig, Long> {

    Optional<JppaConfig> findByCodDominio(String codDominio);
}
