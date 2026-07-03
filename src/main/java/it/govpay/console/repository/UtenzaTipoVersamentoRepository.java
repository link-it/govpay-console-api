package it.govpay.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.UtenzaTipoVersamento;

public interface UtenzaTipoVersamentoRepository extends JpaRepository<UtenzaTipoVersamento, Long> {

    List<UtenzaTipoVersamento> findByIdUtenza(Long idUtenza);

    void deleteByIdUtenza(Long idUtenza);
}
