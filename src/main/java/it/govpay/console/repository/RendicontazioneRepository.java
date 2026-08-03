package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.Rendicontazione;

public interface RendicontazioneRepository extends JpaRepository<Rendicontazione, Long> {
}
