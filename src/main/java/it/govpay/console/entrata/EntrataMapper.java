package it.govpay.console.entrata;

import java.util.Map;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.TipoTributo;
import it.govpay.console.model.Entrata;
import it.govpay.console.model.EntrataSummary;
import it.govpay.console.model.TipoContabilita;
import it.govpay.console.web.BadRequestException;

@Component
public class EntrataMapper {

    /**
     * Codifica DB (colonna {@code tipo_contabilita VARCHAR(1)}) dei valori enum,
     * allineata a V1 ({@code Tributo.TipoContabilita}).
     */
    private static final Map<TipoContabilita, String> ENUM_TO_CODIFICA = Map.of(
            TipoContabilita.CAPITOLO, "0",
            TipoContabilita.SPECIALE, "1",
            TipoContabilita.SIOPE, "2",
            TipoContabilita.SRTP_ESCLUSA_RAVV_OPEROSO, "6",
            TipoContabilita.SRTP_ESCLUSA_ALTRO_OPERATORE, "7",
            TipoContabilita.SRTP_ESCLUSA, "8",
            TipoContabilita.ALTRO, "9");

    public EntrataSummary toSummary(TipoTributo entity) {
        EntrataSummary dto = new EntrataSummary();
        dto.setIdEntrata(entity.getCodTributo());
        dto.setDescrizione(entity.getDescrizione());
        return dto;
    }

    public Entrata toDetail(TipoTributo entity) {
        Entrata dto = new Entrata();
        dto.setIdEntrata(entity.getCodTributo());
        dto.setDescrizione(entity.getDescrizione());
        dto.setTipoContabilita(toEnum(entity.getTipoContabilita()));
        dto.setCodiceContabilita(entity.getCodContabilita());
        return dto;
    }

    public String toCodifica(TipoContabilita value) {
        return value == null ? null : ENUM_TO_CODIFICA.get(value);
    }

    public TipoContabilita toEnum(String codifica) {
        if (codifica == null) {
            return null;
        }
        for (Map.Entry<TipoContabilita, String> e : ENUM_TO_CODIFICA.entrySet()) {
            if (e.getValue().equals(codifica)) {
                return e.getKey();
            }
        }
        throw new BadRequestException(
                "Codifica tipoContabilita' non riconosciuta sul dato persistito: '" + codifica + "'.");
    }
}
