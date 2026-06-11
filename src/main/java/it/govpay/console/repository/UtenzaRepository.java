package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.Utenza;

public interface UtenzaRepository extends JpaRepository<Utenza, Long> {

    Optional<Utenza> findByPrincipal(String principal);
}
