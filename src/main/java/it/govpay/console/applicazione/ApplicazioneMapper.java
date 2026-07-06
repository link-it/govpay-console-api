package it.govpay.console.applicazione;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.ApplicazioneLinks;
import it.govpay.console.model.ApplicazioneSummary;
import it.govpay.console.model.CodificaAvvisi;
import it.govpay.console.model.Link;
import it.govpay.console.model.TipoPendenzaRef;
import it.govpay.console.utenza.UtenzaAssociazioniMapper;

/**
 * Assembla {@code ApplicazioneSummary} / {@code Applicazione}. Le associazioni
 * utenza (domini/tipiPendenza/ruoli/acl) sono delegate a
 * {@link UtenzaAssociazioniMapper}; qui restano le parti specifiche
 * dell'applicazione: {@code codificaAvvisi}, il placeholder
 * {@code autodeterminazione} (flag {@code trusted}) e i {@code _links}.
 */
@Component
public class ApplicazioneMapper {

    static final String AUTODETERMINAZIONE_ID = "autodeterminazione";
    static final String AUTODETERMINAZIONE_LABEL = "Autodeterminazione delle Pendenze";

    private final UtenzaAssociazioniMapper associazioni;

    public ApplicazioneMapper(UtenzaAssociazioniMapper associazioni) {
        this.associazioni = associazioni;
    }

    public ApplicazioneSummary toSummary(Applicazione entity) {
        Utenza utenza = entity.getUtenza();
        ApplicazioneSummary dto = new ApplicazioneSummary();
        dto.setIdA2A(entity.getCodApplicazione());
        dto.setPrincipal(utenza.getPrincipalOriginale());
        dto.setAbilitato(utenza.getAbilitato());
        return dto;
    }

    public it.govpay.console.model.Applicazione toDetail(Applicazione entity) {
        Utenza utenza = entity.getUtenza();

        it.govpay.console.model.Applicazione dto = new it.govpay.console.model.Applicazione();
        dto.setIdA2A(entity.getCodApplicazione());
        dto.setPrincipal(utenza.getPrincipalOriginale());
        dto.setAbilitato(utenza.getAbilitato());
        dto.setCodificaAvvisi(buildCodificaAvvisi(entity));
        dto.setDomini(associazioni.buildDomini(utenza));
        dto.setTipiPendenza(buildTipiPendenza(entity, utenza));
        dto.setRuoli(associazioni.buildRuoli(utenza.getRuoli()));
        dto.setAcl(associazioni.buildAcl(utenza.getId()));
        dto.setLinks(buildLinks(entity.getCodApplicazione()));
        return dto;
    }

    private List<TipoPendenzaRef> buildTipiPendenza(Applicazione entity, Utenza utenza) {
        List<TipoPendenzaRef> out = new ArrayList<>(associazioni.buildTipiPendenza(utenza));
        if (Boolean.TRUE.equals(entity.getTrusted())) {
            out.add(UtenzaAssociazioniMapper.tipoPendenzaRef(AUTODETERMINAZIONE_ID, AUTODETERMINAZIONE_LABEL));
        }
        return out;
    }

    private static CodificaAvvisi buildCodificaAvvisi(Applicazione entity) {
        boolean hasCodifica = notBlank(entity.getCodApplicazioneIuv()) || notBlank(entity.getRegExp());
        boolean autoIuv = Boolean.TRUE.equals(entity.getAutoIuv());
        if (hasCodifica) {
            CodificaAvvisi c = new CodificaAvvisi();
            c.setCodificaIuv(entity.getCodApplicazioneIuv());
            c.setRegExpIuv(entity.getRegExp());
            c.setGenerazioneIuvInterna(autoIuv);
            return c;
        }
        if (autoIuv) {
            CodificaAvvisi c = new CodificaAvvisi();
            c.setGenerazioneIuvInterna(true);
            return c;
        }
        return null;
    }

    private static ApplicazioneLinks buildLinks(String idA2A) {
        ApplicazioneLinks links = new ApplicazioneLinks();
        links.setConnettoreIntegrazione(new Link("/applicazioni/" + idA2A + "/connettore-integrazione"));
        return links;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
