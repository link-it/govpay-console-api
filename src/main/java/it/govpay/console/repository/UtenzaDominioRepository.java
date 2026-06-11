package it.govpay.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.UtenzaDominio;

public interface UtenzaDominioRepository extends JpaRepository<UtenzaDominio, Long> {

    List<UtenzaDominio> findByIdUtenza(Long idUtenza);
}
