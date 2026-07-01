package it.govpay.console.contoaccredito;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.model.ContoAccreditoSummary;

@Component
public class ContoAccreditoMapper {

    public ContoAccreditoSummary toSummary(IbanAccredito entity) {
        ContoAccreditoSummary dto = new ContoAccreditoSummary();
        dto.setIbanAccredito(entity.getCodIban());
        dto.setDescrizione(entity.getDescrizione());
        dto.setAbilitato(entity.getAbilitato());
        return dto;
    }

    public it.govpay.console.model.ContoAccredito toDetail(IbanAccredito entity) {
        it.govpay.console.model.ContoAccredito dto = new it.govpay.console.model.ContoAccredito();
        dto.setIbanAccredito(entity.getCodIban());
        dto.setBic(entity.getBicAccredito());
        dto.setPostale(entity.getPostale());
        dto.setAbilitato(entity.getAbilitato());
        dto.setDescrizione(entity.getDescrizione());
        dto.setIntestatario(entity.getIntestatario());
        dto.setAutStampaPosteItaliane(entity.getAutStampaPoste());
        return dto;
    }
}
