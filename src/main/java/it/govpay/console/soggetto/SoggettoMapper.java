package it.govpay.console.soggetto;

import org.springframework.stereotype.Component;

import it.govpay.console.model.Soggetto;
import it.govpay.console.model.Soggetto.TipoEnum;
import it.govpay.pendenze.entity.Debitore;

/**
 * Mapper {@link Debitore} → {@link Soggetto} per l'endpoint
 * {@code /pendenze/{idA2A}/{idPendenza}/informazioniDebitore}.
 *
 * <p>Schema {@code Soggetto} allineato a V1 ({@code soggetto},
 * govpay-api-backoffice-v1.yaml:8306). Tutti i campi sono mappati dal debitore.
 */
@Component
public class SoggettoMapper {

    public Soggetto toSoggetto(Debitore d) {
        Soggetto s = new Soggetto(
                d.getTipo() != null ? TipoEnum.valueOf(d.getTipo().name()) : TipoEnum.F,
                d.getIdentificativo(),
                d.getAnagrafica());
        s.setIndirizzo(d.getIndirizzo());
        s.setCivico(d.getCivico());
        s.setCap(d.getCap());
        s.setLocalita(d.getLocalita());
        s.setProvincia(d.getProvincia());
        s.setNazione(d.getNazione());
        s.setEmail(d.getEmail());
        s.setCellulare(d.getCellulare());
        return s;
    }
}
