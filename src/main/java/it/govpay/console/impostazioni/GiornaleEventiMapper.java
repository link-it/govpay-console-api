package it.govpay.console.impostazioni;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.GiornaleEventiInterfaccia;
import it.govpay.console.model.GdeEvento;
import it.govpay.console.model.GdeEvento.DumpEnum;
import it.govpay.console.model.GdeEvento.LogEnum;
import it.govpay.console.model.GdeInterfaccia;
import it.govpay.console.model.ImpostazioniGiornaleEventi;

/**
 * Conversione bidirezionale tra {@link ImpostazioniGiornaleEventi} (8 blocchi
 * {@code GdeInterfaccia}, uno per interfaccia API) e le 8 righe di
 * {@code giornale_eventi_interfacce}, chiave naturale {@code nome_interfaccia}.
 */
@Component
public class GiornaleEventiMapper {

    /** Nomi delle interfacce, stesso ordine/naming di V1 (set fisso, mai esteso a runtime). */
    public static final List<String> NOMI_INTERFACCE = List.of(
            "apiEnte", "apiPagamento", "apiRagioneria", "apiBackoffice",
            "apiPagoPA", "apiPendenze", "apiBackendIO", "apiMaggioliJPPA");

    private static final String DEFAULT_POLICY = "MAI";

    public ImpostazioniGiornaleEventi toDto(Map<String, GiornaleEventiInterfaccia> byNome) {
        ImpostazioniGiornaleEventi dto = new ImpostazioniGiornaleEventi();
        dto.setApiEnte(toGdeInterfaccia(byNome.get("apiEnte")));
        dto.setApiPagamento(toGdeInterfaccia(byNome.get("apiPagamento")));
        dto.setApiRagioneria(toGdeInterfaccia(byNome.get("apiRagioneria")));
        dto.setApiBackoffice(toGdeInterfaccia(byNome.get("apiBackoffice")));
        dto.setApiPagoPA(toGdeInterfaccia(byNome.get("apiPagoPA")));
        dto.setApiPendenze(toGdeInterfaccia(byNome.get("apiPendenze")));
        dto.setApiBackendIO(toGdeInterfaccia(byNome.get("apiBackendIO")));
        dto.setApiMaggioliJPPA(toGdeInterfaccia(byNome.get("apiMaggioliJPPA")));
        return dto;
    }

    public Map<String, GiornaleEventiInterfaccia> toEntities(ImpostazioniGiornaleEventi dto) {
        Map<String, GiornaleEventiInterfaccia> map = new LinkedHashMap<>();
        map.put("apiEnte", toEntity("apiEnte", dto.getApiEnte()));
        map.put("apiPagamento", toEntity("apiPagamento", dto.getApiPagamento()));
        map.put("apiRagioneria", toEntity("apiRagioneria", dto.getApiRagioneria()));
        map.put("apiBackoffice", toEntity("apiBackoffice", dto.getApiBackoffice()));
        map.put("apiPagoPA", toEntity("apiPagoPA", dto.getApiPagoPA()));
        map.put("apiPendenze", toEntity("apiPendenze", dto.getApiPendenze()));
        map.put("apiBackendIO", toEntity("apiBackendIO", dto.getApiBackendIO()));
        map.put("apiMaggioliJPPA", toEntity("apiMaggioliJPPA", dto.getApiMaggioliJPPA()));
        return map;
    }

    private GdeInterfaccia toGdeInterfaccia(GiornaleEventiInterfaccia entity) {
        GdeInterfaccia dto = new GdeInterfaccia();
        if (entity == null) {
            dto.setLetture(defaultEvento());
            dto.setScritture(defaultEvento());
            return dto;
        }
        dto.setLetture(new GdeEvento(LogEnum.fromValue(entity.getLogLetture()), DumpEnum.fromValue(entity.getDumpLetture())));
        dto.setScritture(new GdeEvento(LogEnum.fromValue(entity.getLogScritture()), DumpEnum.fromValue(entity.getDumpScritture())));
        return dto;
    }

    private static GdeEvento defaultEvento() {
        return new GdeEvento(LogEnum.fromValue(DEFAULT_POLICY), DumpEnum.fromValue(DEFAULT_POLICY));
    }

    private GiornaleEventiInterfaccia toEntity(String nomeInterfaccia, GdeInterfaccia dto) {
        GiornaleEventiInterfaccia entity = new GiornaleEventiInterfaccia();
        entity.setNomeInterfaccia(nomeInterfaccia);
        GdeEvento letture = dto != null ? dto.getLetture() : null;
        GdeEvento scritture = dto != null ? dto.getScritture() : null;
        entity.setLogLetture(letture != null && letture.getLog() != null ? letture.getLog().getValue() : DEFAULT_POLICY);
        entity.setDumpLetture(letture != null && letture.getDump() != null ? letture.getDump().getValue() : DEFAULT_POLICY);
        entity.setLogScritture(scritture != null && scritture.getLog() != null ? scritture.getLog().getValue() : DEFAULT_POLICY);
        entity.setDumpScritture(scritture != null && scritture.getDump() != null ? scritture.getDump().getValue() : DEFAULT_POLICY);
        return entity;
    }
}
