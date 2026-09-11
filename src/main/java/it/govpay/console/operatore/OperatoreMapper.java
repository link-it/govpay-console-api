package it.govpay.console.operatore;

import org.springframework.stereotype.Component;

import it.govpay.console.common.PreferenzeCodec;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.OperatoreSummary;
import it.govpay.console.utenza.UtenzaAssociazioniMapper;
import tools.jackson.databind.ObjectMapper;

/**
 * Assembla {@code OperatoreSummary} / {@code Operatore}. Le associazioni utenza
 * (domini/tipiPendenza/ruoli/acl) sono delegate a {@link UtenzaAssociazioniMapper};
 * la parte specifica dell'operatore e' {@code nome} e {@code preferenze}. Nessun
 * placeholder {@code autodeterminazione} (non si applica agli operatori).
 */
@Component
public class OperatoreMapper {

    private final UtenzaAssociazioniMapper associazioni;
    private final ObjectMapper objectMapper;

    public OperatoreMapper(UtenzaAssociazioniMapper associazioni, ObjectMapper objectMapper) {
        this.associazioni = associazioni;
        this.objectMapper = objectMapper;
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
        dto.setPreferenze(PreferenzeCodec.parse(op.getPreferenze(), objectMapper));
        return dto;
    }
}
