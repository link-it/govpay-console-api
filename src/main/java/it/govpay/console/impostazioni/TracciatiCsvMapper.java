package it.govpay.console.impostazioni;

import org.springframework.stereotype.Component;

import it.govpay.common.configurazione.model.TracciatoCsv;
import it.govpay.console.model.TipoTemplateTrasformazione;

/**
 * Conversione bidirezionale tra {@link it.govpay.console.model.ImpostazioniTracciatiCsv}
 * e {@link TracciatoCsv} (bean di {@code govpay-common}, deserializzato dal
 * blob {@code configurazione}).
 */
@Component
public class TracciatiCsvMapper {

    public it.govpay.console.model.ImpostazioniTracciatiCsv toDto(TracciatoCsv source) {
        it.govpay.console.model.ImpostazioniTracciatiCsv dto = new it.govpay.console.model.ImpostazioniTracciatiCsv();
        dto.setTipo(TipoTemplateTrasformazione.FREEMARKER);
        dto.setIntestazione(source.getIntestazione());
        dto.setRichiesta(source.getRichiesta());
        dto.setRisposta(source.getRisposta());
        return dto;
    }

    public void applyConfig(TracciatoCsv target, it.govpay.console.model.ImpostazioniTracciatiCsv dto) {
        target.setTipo(TipoTemplateTrasformazione.FREEMARKER.getValue());
        target.setIntestazione(dto.getIntestazione());
        target.setRichiesta(dto.getRichiesta());
        target.setRisposta(dto.getRisposta());
    }
}
