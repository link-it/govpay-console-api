package it.govpay.console.impostazioni;

import it.govpay.common.configurazione.model.Giornale;
import org.springframework.stereotype.Component;

import it.govpay.common.configurazione.model.GdeInterfaccia;
import it.govpay.console.model.GdeEvento;
import it.govpay.console.model.GdeEvento.DumpEnum;
import it.govpay.console.model.GdeEvento.LogEnum;
import it.govpay.console.model.ImpostazioniGiornaleEventi;

/**
 * Conversione bidirezionale tra {@link ImpostazioniGiornaleEventi} e
 * {@link Giornale} (bean di {@code govpay-common}, stessi 8 campi/nomi,
 * deserializzato dal blob {@code configurazione}, chiave {@code giornale_eventi}).
 * Il connettore GDE vero e proprio (url/auth) e' la sotto-risorsa indipendente
 * {@link ServizioGdeService}, referenziata via {@code _links}.
 */
@Component
public class GiornaleEventiMapper {

    private static final String DEFAULT_POLICY = "MAI";

    public ImpostazioniGiornaleEventi toDto(Giornale source) {
        ImpostazioniGiornaleEventi dto = new ImpostazioniGiornaleEventi();
        dto.setApiEnte(toApiInterfaccia(source.getApiEnte()));
        dto.setApiPagamento(toApiInterfaccia(source.getApiPagamento()));
        dto.setApiRagioneria(toApiInterfaccia(source.getApiRagioneria()));
        dto.setApiBackoffice(toApiInterfaccia(source.getApiBackoffice()));
        dto.setApiPagoPA(toApiInterfaccia(source.getApiPagoPA()));
        dto.setApiPendenze(toApiInterfaccia(source.getApiPendenze()));
        dto.setApiBackendIO(toApiInterfaccia(source.getApiBackendIO()));
        dto.setApiMaggioliJPPA(toApiInterfaccia(source.getApiMaggioliJPPA()));
        return dto;
    }

    public Giornale toCommon(ImpostazioniGiornaleEventi dto) {
        Giornale target = new Giornale();
        target.setApiEnte(toCommonInterfaccia(dto.getApiEnte()));
        target.setApiPagamento(toCommonInterfaccia(dto.getApiPagamento()));
        target.setApiRagioneria(toCommonInterfaccia(dto.getApiRagioneria()));
        target.setApiBackoffice(toCommonInterfaccia(dto.getApiBackoffice()));
        target.setApiPagoPA(toCommonInterfaccia(dto.getApiPagoPA()));
        target.setApiPendenze(toCommonInterfaccia(dto.getApiPendenze()));
        target.setApiBackendIO(toCommonInterfaccia(dto.getApiBackendIO()));
        target.setApiMaggioliJPPA(toCommonInterfaccia(dto.getApiMaggioliJPPA()));
        return target;
    }

    private static it.govpay.console.model.GdeInterfaccia toApiInterfaccia(GdeInterfaccia source) {
        it.govpay.console.model.GdeInterfaccia dto = new it.govpay.console.model.GdeInterfaccia();
        if (source == null) {
            dto.setLetture(defaultEvento());
            dto.setScritture(defaultEvento());
            return dto;
        }
        dto.setLetture(toApiEvento(source.getLetture()));
        dto.setScritture(toApiEvento(source.getScritture()));
        return dto;
    }

    private static GdeEvento toApiEvento(it.govpay.common.configurazione.model.GdeEvento source) {
        if (source == null) {
            return defaultEvento();
        }
        LogEnum log = source.getLog() != null ? LogEnum.fromValue(source.getLog().name()) : LogEnum.fromValue(DEFAULT_POLICY);
        DumpEnum dump = source.getDump() != null ? DumpEnum.fromValue(source.getDump().name()) : DumpEnum.fromValue(DEFAULT_POLICY);
        return new GdeEvento(log, dump);
    }

    private static GdeEvento defaultEvento() {
        return new GdeEvento(LogEnum.fromValue(DEFAULT_POLICY), DumpEnum.fromValue(DEFAULT_POLICY));
    }

    private static GdeInterfaccia toCommonInterfaccia(it.govpay.console.model.GdeInterfaccia dto) {
        GdeInterfaccia target = new GdeInterfaccia();
        target.setLetture(toCommonEvento(dto != null ? dto.getLetture() : null));
        target.setScritture(toCommonEvento(dto != null ? dto.getScritture() : null));
        return target;
    }

    private static it.govpay.common.configurazione.model.GdeEvento toCommonEvento(GdeEvento dto) {
        it.govpay.common.configurazione.model.GdeEvento target = new it.govpay.common.configurazione.model.GdeEvento();
        String log = dto != null && dto.getLog() != null ? dto.getLog().getValue() : DEFAULT_POLICY;
        String dump = dto != null && dto.getDump() != null ? dto.getDump().getValue() : DEFAULT_POLICY;
        target.setLog(it.govpay.common.configurazione.model.GdeEvento.LogEnum.valueOf(log));
        target.setDump(it.govpay.common.configurazione.model.GdeEvento.DumpEnum.valueOf(dump));
        return target;
    }
}
