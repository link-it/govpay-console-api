package it.govpay.console.eventi;

import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Dominio;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.security.OperatoreCorrente;

/**
 * Risoluzione dell'ACL su {@code idDominio} per il giornale eventi, condivisa
 * tra {@link EventoSearchService} (lista) e {@link EventoDetailService}
 * (dettaglio + sub-resource). Stesso criterio di {@code DominioVisibilita}
 * (solo i domini "interi" dell'operatore, nessuna estensione via UO — gli
 * eventi correlano solo a {@code idDominio}, non a una UO specifica). Un
 * evento senza {@code idDominio} (batch di sistema, scheduler) e' visibile
 * solo a operatori con {@code tuttiIDomini}.
 */
@Component
public class EventoAcl {

    private final DominioRepository dominioRepository;

    public EventoAcl(DominioRepository dominioRepository) {
        this.dominioRepository = dominioRepository;
    }

    /** Codici dominio "interi" dell'operatore. Vuota se l'operatore non ne ha nessuno. */
    public List<String> codiciVisibili(OperatoreCorrente operatore) {
        if (operatore.idDominiInteri().isEmpty()) {
            return List.of();
        }
        return dominioRepository.findAllById(operatore.idDominiInteri()).stream()
                .map(Dominio::getCodDominio)
                .toList();
    }

    /** Check post-fetch su un {@code idDominio} gia' caricato (per i dettagli). */
    public boolean isVisibile(String idDominio, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            return true;
        }
        return idDominio != null && codiciVisibili(operatore).contains(idDominio);
    }
}
