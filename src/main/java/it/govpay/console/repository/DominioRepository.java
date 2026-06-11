package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.Dominio;

public interface DominioRepository extends JpaRepository<Dominio, Long> {
}
