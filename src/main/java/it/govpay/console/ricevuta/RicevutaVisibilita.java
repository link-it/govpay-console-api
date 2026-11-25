package it.govpay.console.ricevuta;

import java.util.Set;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Dominio;
import it.govpay.console.pendenza.PendenzaLettura;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.ConsoleRptRepository.RptConPendenza;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.PendenzaVisibilita;

/**
 * Visibilita' di una ricevuta per l'operatore corrente. Una ricevuta con pendenza segue la
 * visibilita' della pendenza. Una ricevuta senza pendenza non ha unita' operativa ne' tipo
 * pendenza: e' visibile a chi ha il suo dominio per intero o la unita' operativa {@code EC} di
 * quel dominio, e il limite sui tipi pendenza non si applica.
 */
@Component
public class RicevutaVisibilita {

    private final PendenzaLettura pendenzaLettura;
    private final ConsoleDominioRepository dominioRepository;
    private final UnitaOperativaRepository unitaOperativaRepository;

    public RicevutaVisibilita(PendenzaLettura pendenzaLettura,
                              ConsoleDominioRepository dominioRepository,
                              UnitaOperativaRepository unitaOperativaRepository) {
        this.pendenzaLettura = pendenzaLettura;
        this.dominioRepository = dominioRepository;
        this.unitaOperativaRepository = unitaOperativaRepository;
    }

    public boolean isVisibile(RptConPendenza caricata, OperatoreCorrente operatore) {
        if (caricata.pendenza() != null) {
            return pendenzaLettura.isVisibile(caricata.pendenza(), operatore);
        }
        if (operatore.tuttiIDomini()) {
            return true;
        }
        Set<Long> dominiConUoEc = operatore.idUoVisibili().isEmpty() ? Set.of()
                : unitaOperativaRepository.findDominioIdsConUoEcTra(operatore.idUoVisibili());
        Set<Long> visibili = PendenzaVisibilita.dominiVisibiliSenzaUnitaOperativa(dominiConUoEc, operatore);
        return dominioRepository.findByCodDominio(caricata.rpt().getCodDominio())
                .map(Dominio::getId)
                .filter(visibili::contains)
                .isPresent();
    }
}
