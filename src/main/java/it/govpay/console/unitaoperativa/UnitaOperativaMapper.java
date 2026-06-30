package it.govpay.console.unitaoperativa;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.model.UnitaOperativaSummary;

@Component
public class UnitaOperativaMapper {

    public UnitaOperativaSummary toSummary(UnitaOperativa entity) {
        UnitaOperativaSummary dto = new UnitaOperativaSummary();
        dto.setIdUnitaOperativa(entity.getCodUo());
        dto.setRagioneSociale(entity.getUoDenominazione());
        dto.setAbilitato(entity.getAbilitato());
        return dto;
    }

    public it.govpay.console.model.UnitaOperativa toDetail(UnitaOperativa entity) {
        it.govpay.console.model.UnitaOperativa dto = new it.govpay.console.model.UnitaOperativa();
        dto.setIdUnitaOperativa(entity.getCodUo());
        dto.setRagioneSociale(entity.getUoDenominazione());
        dto.setAbilitato(entity.getAbilitato());
        dto.setIndirizzo(entity.getUoIndirizzo());
        dto.setCivico(entity.getUoCivico());
        dto.setCap(entity.getUoCap());
        dto.setLocalita(entity.getUoLocalita());
        dto.setProvincia(entity.getUoProvincia());
        dto.setNazione(entity.getUoNazione());
        dto.setEmail(entity.getUoEmail());
        dto.setPec(entity.getUoPec());
        dto.setTel(entity.getUoTel());
        dto.setFax(entity.getUoFax());
        dto.setWeb(entity.getUoUrlSitoWeb());
        dto.setArea(entity.getUoArea());
        return dto;
    }
}
