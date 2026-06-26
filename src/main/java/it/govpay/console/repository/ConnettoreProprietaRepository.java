package it.govpay.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.ConnettoreProprieta;

public interface ConnettoreProprietaRepository extends JpaRepository<ConnettoreProprieta, Long> {

    List<ConnettoreProprieta> findByCodConnettore(String codConnettore);
}
