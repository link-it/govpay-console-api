package it.govpay.console.pagopaiban;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.IbanCache;
import it.govpay.console.model.IbanPagoPa;

@Component
public class IbanPagoPaMapper {

    public IbanPagoPa toDto(IbanCache entity) {
        IbanPagoPa dto = new IbanPagoPa();
        dto.setIban(entity.getIban());
        dto.setAttivo(entity.getAttivo());
        dto.setDataUltimaVerificaPagopa(entity.getDataUltimaVerifica());
        return dto;
    }
}
