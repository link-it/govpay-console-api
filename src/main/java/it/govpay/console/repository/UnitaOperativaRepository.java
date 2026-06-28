package it.govpay.console.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.govpay.console.entity.UnitaOperativa;

public interface UnitaOperativaRepository extends JpaRepository<UnitaOperativa, Long> {

    Optional<UnitaOperativa> findByDominio_IdAndCodUo(Long idDominio, String codUo);

    /**
     * Proietta gli id dei domini padre per un set di id di UO. Usato da
     * {@code ProfiloService} per ricostruire la lista dei domini visibili
     * dall'operatore quando l'autorizzazione esiste solo a livello di UO
     * (riga in {@code utenze_domini} con {@code id_uo IS NOT NULL}).
     * Evita il fetch N+1 di {@code uo.getDominio()} che avverrebbe usando
     * {@link #findAllById(Iterable)}.
     */
    @Query("SELECT DISTINCT u.dominio.id FROM UnitaOperativa u WHERE u.id IN :ids")
    Set<Long> findDistinctDominioIdsByIdIn(@Param("ids") Set<Long> ids);
}
