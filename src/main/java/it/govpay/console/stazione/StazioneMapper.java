package it.govpay.console.stazione;

import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Stazione;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.StazioneSummary;
import it.govpay.console.model.VersioneStazione;

@Component
public class StazioneMapper {

    public StazioneSummary toSummary(Stazione entity) {
        StazioneSummary dto = new StazioneSummary();
        dto.setIdStazione(entity.getCodStazione());
        dto.setVersione(VersioneStazione.fromValue(entity.getVersione()));
        dto.setAbilitato(entity.getAbilitato());
        return dto;
    }

    public it.govpay.console.model.Stazione toDetail(Stazione entity, List<Dominio> domini) {
        it.govpay.console.model.Stazione dto = new it.govpay.console.model.Stazione();
        dto.setIdStazione(entity.getCodStazione());
        dto.setVersione(VersioneStazione.fromValue(entity.getVersione()));
        dto.setAbilitato(entity.getAbilitato());
        dto.setDomini(domini.stream().map(StazioneMapper::toDominioRef).toList());
        return dto;
    }

    private static DominioRef toDominioRef(Dominio dominio) {
        DominioRef ref = new DominioRef();
        ref.setIdDominio(dominio.getCodDominio());
        ref.setRagioneSociale(dominio.getRagioneSociale());
        return ref;
    }
}
