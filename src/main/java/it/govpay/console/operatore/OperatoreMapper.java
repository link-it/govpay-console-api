package it.govpay.console.operatore;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.OperatoreSummary;
import it.govpay.console.utenza.UtenzaAssociazioniMapper;

/**
 * Assembla {@code OperatoreSummary} / {@code Operatore}. Le associazioni utenza
 * (domini/tipiPendenza/ruoli/acl) sono delegate a {@link UtenzaAssociazioniMapper};
 * la parte specifica dell'operatore e' il {@code nome}. Nessun placeholder
 * {@code autodeterminazione} (non si applica agli operatori).
 */
@Component
public class OperatoreMapper {

    private final UtenzaAssociazioniMapper associazioni;

    public OperatoreMapper(UtenzaAssociazioniMapper associazioni) {
        this.associazioni = associazioni;
    }

    public OperatoreSummary toSummary(Operatore op, Utenza utenza) {
        OperatoreSummary dto = new OperatoreSummary();
        dto.setPrincipal(utenza.getPrincipalOriginale());
        dto.setNome(op.getNome());
        dto.setAbilitato(utenza.getAbilitato());
        return dto;
    }

    public it.govpay.console.model.Operatore toDetail(Operatore op, Utenza utenza) {
        it.govpay.console.model.Operatore dto = new it.govpay.console.model.Operatore();
        dto.setPrincipal(utenza.getPrincipalOriginale());
        dto.setNome(op.getNome());
        dto.setAbilitato(utenza.getAbilitato());
        dto.setDomini(associazioni.buildDomini(utenza));
        dto.setTipiPendenza(associazioni.buildTipiPendenza(utenza));
        dto.setRuoli(associazioni.buildRuoli(utenza.getRuoli()));
        dto.setAcl(associazioni.buildAcl(utenza.getId()));
        return dto;
    }
}
