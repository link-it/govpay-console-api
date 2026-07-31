package it.govpay.console.impostazioni;

import org.springframework.stereotype.Component;

import it.govpay.console.model.TipoTemplateTrasformazione;

/**
 * Conversione bidirezionale tra {@link it.govpay.console.model.ImpostazioniTracciatiCsv}
 * e l'entity JPA (riga singola).
 */
@Component
public class TracciatiCsvMapper {

    public it.govpay.console.model.ImpostazioniTracciatiCsv toDto(it.govpay.console.entity.ImpostazioniTracciatiCsv entity) {
        it.govpay.console.model.ImpostazioniTracciatiCsv dto = new it.govpay.console.model.ImpostazioniTracciatiCsv();
        dto.setTipo(TipoTemplateTrasformazione.FREEMARKER);
        dto.setIntestazione(entity.getIntestazione());
        dto.setRichiesta(entity.getRichiesta());
        dto.setRisposta(entity.getRisposta());
        return dto;
    }

    public void applyConfig(it.govpay.console.entity.ImpostazioniTracciatiCsv entity,
                            it.govpay.console.model.ImpostazioniTracciatiCsv dto) {
        entity.setTipo(TipoTemplateTrasformazione.FREEMARKER.getValue());
        entity.setIntestazione(dto.getIntestazione());
        entity.setRichiesta(dto.getRichiesta());
        entity.setRisposta(dto.getRisposta());
    }
}
