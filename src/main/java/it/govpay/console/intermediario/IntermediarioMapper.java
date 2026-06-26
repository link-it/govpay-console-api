package it.govpay.console.intermediario;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Intermediario;
import it.govpay.console.model.IntermediarioSummary;

@Component
public class IntermediarioMapper {

    public IntermediarioSummary toSummary(Intermediario entity) {
        IntermediarioSummary dto = new IntermediarioSummary();
        dto.setIdIntermediario(entity.getCodIntermediario());
        dto.setDenominazione(entity.getDenominazione());
        dto.setAbilitato(entity.getAbilitato());
        return dto;
    }

    public it.govpay.console.model.Intermediario toDetail(Intermediario entity) {
        it.govpay.console.model.Intermediario dto = new it.govpay.console.model.Intermediario();
        dto.setIdIntermediario(entity.getCodIntermediario());
        dto.setDenominazione(entity.getDenominazione());
        dto.setPrincipalPagoPa(entity.getPrincipal());
        dto.setAbilitato(entity.getAbilitato());
        return dto;
    }
}
