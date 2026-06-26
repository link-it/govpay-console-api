package it.govpay.console.soggetto;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Versamento;
import it.govpay.console.model.Soggetto;
import it.govpay.console.model.Soggetto.TipoEnum;

/**
 * Mapper {@link Versamento} → {@link Soggetto} per l'endpoint
 * {@code /pendenze/{idA2A}/{idPendenza}/informazioniDebitore}.
 *
 * <p>Schema {@code Soggetto} allineato a V1 ({@code soggetto},
 * govpay-api-backoffice-v1.yaml:8306). Tutti i campi sono mappati dai campi
 * {@code debitore_*} della tabella {@code versamenti}.
 */
@Component
public class SoggettoMapper {

    public Soggetto toSoggetto(Versamento v) {
        Soggetto s = new Soggetto(
                mapTipo(v.getDebitoreTipo()),
                v.getDebitoreIdentificativo(),
                v.getDebitoreAnagrafica());
        s.setIndirizzo(v.getDebitoreIndirizzo());
        s.setCivico(v.getDebitoreCivico());
        s.setCap(v.getDebitoreCap());
        s.setLocalita(v.getDebitoreLocalita());
        s.setProvincia(v.getDebitoreProvincia());
        s.setNazione(v.getDebitoreNazione());
        s.setEmail(v.getDebitoreEmail());
        s.setCellulare(v.getDebitoreCellulare());
        return s;
    }

    /**
     * Mappa {@code versamenti.debitore_tipo VARCHAR(1)} sull'enum.
     * {@code F} (persona fisica) e' il default in V1 quando il dato non e'
     * disponibile (vedi V1 yaml schema: {@code tipo} obbligatorio).
     */
    private static TipoEnum mapTipo(String debitoreTipo) {
        if (debitoreTipo == null) {
            return TipoEnum.F;
        }
        return switch (debitoreTipo.toUpperCase()) {
            case "G" -> TipoEnum.G;
            default -> TipoEnum.F;
        };
    }
}
