package it.govpay.console.entratadominio;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Tributo;
import it.govpay.console.entrata.EntrataMapper;
import it.govpay.console.model.EntrataDominio;
import it.govpay.console.model.EntrataDominioSummary;

@Component
public class EntrataDominioMapper {

    private final EntrataMapper entrataMapper;

    public EntrataDominioMapper(EntrataMapper entrataMapper) {
        this.entrataMapper = entrataMapper;
    }

    public EntrataDominioSummary toSummary(Tributo entity) {
        EntrataDominioSummary dto = new EntrataDominioSummary();
        dto.setIdEntrata(entity.getTipoTributo().getCodTributo());
        dto.setDescrizione(entity.getTipoTributo().getDescrizione());
        dto.setAbilitato(entity.getAbilitato());
        return dto;
    }

    public EntrataDominio toDetail(Tributo entity) {
        EntrataDominio dto = new EntrataDominio();
        dto.setIdEntrata(entity.getTipoTributo().getCodTributo());
        dto.setAbilitato(entity.getAbilitato());
        dto.setIbanAccredito(codIban(entity.getIbanAccredito()));
        dto.setIbanAppoggio(codIban(entity.getIbanAppoggio()));
        dto.setTipoContabilita(entrataMapper.toEnum(entity.getTipoContabilita()));
        dto.setCodiceContabilita(entity.getCodiceContabilita());
        dto.setTipoEntrata(entrataMapper.toDetail(entity.getTipoTributo()));
        return dto;
    }

    private static String codIban(IbanAccredito iban) {
        return iban == null ? null : iban.getCodIban();
    }
}
