package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.Utenza;

public interface UtenzaRepository extends JpaRepository<Utenza, Long> {

    Optional<Utenza> findByPrincipal(String principal);

    /**
     * Verifica la disponibilita' del valore esposto dall'API (username/idA2A),
     * che corrisponde a {@code principalOriginale}: le risorse Operatore e
     * Applicazione sono caricate/identificate tramite questo campo, quindi la
     * sua unicita' va garantita in creazione (allineato a V1
     * {@code UtentiDAO}/{@code ApplicazioniDAO}).
     */
    boolean existsByPrincipalOriginale(String principalOriginale);
}
