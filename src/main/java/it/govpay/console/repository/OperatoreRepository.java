package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.Operatore;

public interface OperatoreRepository extends JpaRepository<Operatore, Long> {

    Optional<Operatore> findByIdUtenza(Long idUtenza);
}
