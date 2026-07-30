package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.ImpostazioniMailPromemoria;

public interface ImpostazioniMailPromemoriaRepository extends JpaRepository<ImpostazioniMailPromemoria, String> {
}
