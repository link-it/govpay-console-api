package it.govpay.console.applicazione;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.Acl;
import it.govpay.console.model.ApplicazioneLinks;
import it.govpay.console.model.ApplicazioneSummary;
import it.govpay.console.model.CodificaAvvisi;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.Link;
import it.govpay.console.model.RuoloRef;
import it.govpay.console.model.TipoPendenzaRef;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;

/**
 * Assembla le rappresentazioni {@code ApplicazioneSummary} / {@code Applicazione}
 * a partire dall'entita' {@link Applicazione} e dalle associazioni memorizzate
 * su {@code utenze_domini}, {@code utenze_tipo_vers}, {@code acl} e sul CSV
 * {@code utenze.ruoli}. Le collezioni con autorizzazione "star" espongono il
 * placeholder {@code {id:"*", desc:"Tutti"}} (compatibilita' V1); il flag
 * {@code trusted} e' esposto come tipo pendenza placeholder {@code autodeterminazione}.
 */
@Component
public class ApplicazioneMapper {

    static final String STAR_ID = "*";
    static final String STAR_LABEL = "Tutti";
    static final String AUTODETERMINAZIONE_ID = "autodeterminazione";
    static final String AUTODETERMINAZIONE_LABEL = "Autodeterminazione delle Pendenze";

    private static final Map<String, Acl.ServizioEnum> SERVIZIO_LOOKUP = Arrays.stream(Acl.ServizioEnum.values())
            .collect(Collectors.toMap(Acl.ServizioEnum::getValue, e -> e));

    private final AclRepository aclRepository;
    private final UtenzaDominioRepository utenzaDominioRepository;
    private final UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;
    private final DominioRepository dominioRepository;
    private final TipoVersamentoRepository tipoVersamentoRepository;

    public ApplicazioneMapper(AclRepository aclRepository,
                              UtenzaDominioRepository utenzaDominioRepository,
                              UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository,
                              DominioRepository dominioRepository,
                              TipoVersamentoRepository tipoVersamentoRepository) {
        this.aclRepository = aclRepository;
        this.utenzaDominioRepository = utenzaDominioRepository;
        this.utenzaTipoVersamentoRepository = utenzaTipoVersamentoRepository;
        this.dominioRepository = dominioRepository;
        this.tipoVersamentoRepository = tipoVersamentoRepository;
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
        long idUtenza = utenza.getId();

        it.govpay.console.model.Applicazione dto = new it.govpay.console.model.Applicazione();
        dto.setIdA2A(entity.getCodApplicazione());
        dto.setPrincipal(utenza.getPrincipalOriginale());
        dto.setAbilitato(utenza.getAbilitato());
        dto.setCodificaAvvisi(buildCodificaAvvisi(entity));
        dto.setDomini(buildDomini(utenza, idUtenza));
        dto.setTipiPendenza(buildTipiPendenza(utenza, entity.getTrusted(), idUtenza));
        dto.setRuoli(buildRuoli(utenza.getRuoli()));
        dto.setAcl(buildAcl(idUtenza));
        dto.setLinks(buildLinks(entity.getCodApplicazione()));
        return dto;
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

    private List<DominioRef> buildDomini(Utenza utenza, long idUtenza) {
        if (Boolean.TRUE.equals(utenza.getAutorizzazioneDominiStar())) {
            return List.of(dominioRef(STAR_ID, STAR_LABEL));
        }
        Set<Long> idDomini = utenzaDominioRepository.findByIdUtenza(idUtenza).stream()
                .map(it.govpay.console.entity.UtenzaDominio::getIdDominio)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (idDomini.isEmpty()) {
            return List.of();
        }
        return dominioRepository.findAllById(idDomini).stream()
                .map(ApplicazioneMapper::toDominioRef)
                .toList();
    }

    private List<TipoPendenzaRef> buildTipiPendenza(Utenza utenza, Boolean trusted, long idUtenza) {
        List<TipoPendenzaRef> out = new ArrayList<>();
        if (Boolean.TRUE.equals(utenza.getAutorizzazioneTipiVersStar())) {
            out.add(tipoPendenzaRef(STAR_ID, STAR_LABEL));
        } else {
            Set<Long> ids = utenzaTipoVersamentoRepository.findByIdUtenza(idUtenza).stream()
                    .map(it.govpay.console.entity.UtenzaTipoVersamento::getIdTipoVersamento)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!ids.isEmpty()) {
                tipoVersamentoRepository.findAllById(ids).stream()
                        .map(ApplicazioneMapper::toTipoPendenzaRef)
                        .forEach(out::add);
            }
        }
        if (Boolean.TRUE.equals(trusted)) {
            out.add(tipoPendenzaRef(AUTODETERMINAZIONE_ID, AUTODETERMINAZIONE_LABEL));
        }
        return out;
    }

    private static List<RuoloRef> buildRuoli(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(RuoloRef::new)
                .toList();
    }

    private List<Acl> buildAcl(long idUtenza) {
        return aclRepository.findByIdUtenza(idUtenza).stream()
                .map(ApplicazioneMapper::toAcl)
                .filter(Objects::nonNull)
                .toList();
    }

    private static ApplicazioneLinks buildLinks(String idA2A) {
        ApplicazioneLinks links = new ApplicazioneLinks();
        links.setConnettoreIntegrazione(new Link("/applicazioni/" + idA2A + "/connettore-integrazione"));
        return links;
    }

    private static DominioRef toDominioRef(Dominio dominio) {
        return dominioRef(dominio.getCodDominio(), dominio.getRagioneSociale());
    }

    private static DominioRef dominioRef(String id, String ragioneSociale) {
        DominioRef ref = new DominioRef(id);
        ref.setRagioneSociale(ragioneSociale);
        return ref;
    }

    private static TipoPendenzaRef toTipoPendenzaRef(TipoVersamento tipo) {
        return tipoPendenzaRef(tipo.getCodTipoVersamento(), tipo.getDescrizione());
    }

    private static TipoPendenzaRef tipoPendenzaRef(String id, String descrizione) {
        TipoPendenzaRef ref = new TipoPendenzaRef(id);
        ref.setDescrizione(descrizione);
        return ref;
    }

    private static Acl toAcl(it.govpay.console.entity.Acl entity) {
        Acl.ServizioEnum servizio = SERVIZIO_LOOKUP.get(entity.getServizio());
        if (servizio == null) {
            return null; // servizio fuori dall'enum noto → skip
        }
        Acl acl = new Acl();
        acl.setServizio(servizio);
        acl.setAutorizzazioni(parseDiritti(entity.getDiritti()));
        acl.setRuolo(entity.getRuolo());
        return acl;
    }

    private static List<Acl.AutorizzazioniEnum> parseDiritti(String diritti) {
        if (diritti == null || diritti.isBlank()) {
            return List.of();
        }
        List<Acl.AutorizzazioniEnum> out = new ArrayList<>();
        for (String d : diritti.split(",")) {
            String t = d.trim().toUpperCase();
            if ("R".equals(t)) {
                out.add(Acl.AutorizzazioniEnum.R);
            } else if ("W".equals(t)) {
                out.add(Acl.AutorizzazioniEnum.W);
            }
        }
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
