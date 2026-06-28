package it.govpay.console.dominio;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Intermediario;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.model.IntermediarioRef;
import it.govpay.console.model.DominioSummary;

/**
 * Mappa l'aggregato dominio (riga {@code domini} + unita' operativa
 * {@code cod_uo='EC'} che porta l'anagrafica) verso le proiezioni V2.
 */
@Component
public class DominioMapper {

    public DominioSummary toSummary(Dominio entity) {
        DominioSummary dto = new DominioSummary();
        dto.setIdDominio(entity.getCodDominio());
        dto.setRagioneSociale(entity.getRagioneSociale());
        dto.setAbilitato(entity.getAbilitato());
        return dto;
    }

    /**
     * @param ec unita' operativa {@code EC} con l'anagrafica del dominio, oppure
     *           {@code null} se non ancora presente.
     */
    public it.govpay.console.model.Dominio toDetail(Dominio entity, UnitaOperativa ec) {
        it.govpay.console.model.Dominio dto = new it.govpay.console.model.Dominio();
        dto.setIdDominio(entity.getCodDominio());
        dto.setRagioneSociale(entity.getRagioneSociale());
        dto.setGln(entity.getGln());
        dto.setCbill(entity.getCbill());
        dto.setIuvPrefix(entity.getIuvPrefix());
        dto.setAutStampaPosteItaliane(entity.getAutStampaPoste());
        // auxDigit ha senso solo per i domini intermediati: la colonna e' NOT NULL DEFAULT 0,
        // quindi per i non intermediati e' persistito a 0 ma non va esposto.
        if (!Boolean.FALSE.equals(entity.getIntermediato())) {
            dto.setAuxDigit(entity.getAuxDigit());
        }
        dto.setSegregationCode(entity.getSegregationCode());
        dto.setTassonomiaPagoPA(entity.getTassonomiaPagoPa());
        dto.setIntermediato(entity.getIntermediato());
        dto.setScaricaFr(entity.getScaricaFr());
        dto.setAbilitato(entity.getAbilitato());

        if (ec != null) {
            dto.setIndirizzo(ec.getUoIndirizzo());
            dto.setCivico(ec.getUoCivico());
            dto.setCap(ec.getUoCap());
            dto.setLocalita(ec.getUoLocalita());
            dto.setProvincia(ec.getUoProvincia());
            dto.setNazione(ec.getUoNazione());
            dto.setEmail(ec.getUoEmail());
            dto.setPec(ec.getUoPec());
            dto.setTel(ec.getUoTel());
            dto.setFax(ec.getUoFax());
            dto.setWeb(ec.getUoUrlSitoWeb());
            dto.setArea(ec.getUoArea());
        }

        Stazione stazione = entity.getStazione();
        if (stazione != null) {
            dto.setIdStazione(stazione.getCodStazione());
            Intermediario intermediario = stazione.getIntermediario();
            if (intermediario != null) {
                IntermediarioRef ref = new IntermediarioRef();
                ref.setIdIntermediario(intermediario.getCodIntermediario());
                ref.setDenominazione(intermediario.getDenominazione());
                dto.setRiferimentoIntermediario(ref);
            }
        }
        return dto;
    }
}
