package it.govpay.console.entecreditore;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.EnteCreditoreCache;
import it.govpay.console.model.EnteCreditore;
import it.govpay.console.model.EnteCreditoreSummary;

@Component
public class EnteCreditoreMapper {

    public EnteCreditoreSummary toSummary(EnteCreditoreCache entity) {
        EnteCreditoreSummary dto = new EnteCreditoreSummary();
        dto.setTaxCode(entity.getCodFiscale());
        dto.setCompanyName(entity.getDenominazione());
        return dto;
    }

    public EnteCreditore toDetail(EnteCreditoreCache entity) {
        EnteCreditore dto = new EnteCreditore();
        dto.setTaxCode(entity.getCodFiscale());
        dto.setCompanyName(entity.getDenominazione());
        dto.setStationId(entity.getStationId());
        dto.setAuxDigit(entity.getAuxDigit());
        dto.setSegregationCode(entity.getSegregationCode());
        dto.setCbill(entity.getCbillCode());
        dto.setDataUltimoAggiornamento(entity.getDataUltimoAggiornamento());
        return dto;
    }
}
