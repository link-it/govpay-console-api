package it.govpay.console.utenza;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.govpay.console.common.DirittiCodec;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.UtenzaTipoVersamento;
import it.govpay.console.model.Acl;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.RuoloRef;
import it.govpay.console.model.TipoPendenzaRef;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;

/**
 * Logica di lettura delle associazioni di una utenza (domini, tipi pendenza,
 * ruoli, ACL) condivisa da applicazioni e operatori. Le collezioni con
 * autorizzazione "star" espongono il placeholder {@code {id:"*", label:"Tutti"}}
 * (compatibilita' V1). Non gestisce il placeholder {@code autodeterminazione}
 * (flag {@code trusted}), specifico delle applicazioni.
 */
@Component
public class UtenzaAssociazioniMapper {

    public static final String STAR_ID = "*";
    public static final String STAR_LABEL = "Tutti";

    private static final Map<String, AclServizio> SERVIZIO_LOOKUP = Arrays.stream(AclServizio.values())
            .collect(Collectors.toMap(AclServizio::getValue, e -> e));

    private final AclRepository aclRepository;
    private final UtenzaDominioRepository utenzaDominioRepository;
    private final UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;
    private final DominioRepository dominioRepository;
    private final TipoVersamentoRepository tipoVersamentoRepository;

    public UtenzaAssociazioniMapper(AclRepository aclRepository,
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

    public List<DominioRef> buildDomini(Utenza utenza) {
        if (Boolean.TRUE.equals(utenza.getAutorizzazioneDominiStar())) {
            return List.of(dominioRef(STAR_ID, STAR_LABEL));
        }
        Set<Long> idDomini = utenzaDominioRepository.findByIdUtenza(utenza.getId()).stream()
                .map(UtenzaDominio::getIdDominio)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (idDomini.isEmpty()) {
            return List.of();
        }
        return dominioRepository.findAllById(idDomini).stream()
                .map(UtenzaAssociazioniMapper::toDominioRef)
                .toList();
    }

    public List<TipoPendenzaRef> buildTipiPendenza(Utenza utenza) {
        if (Boolean.TRUE.equals(utenza.getAutorizzazioneTipiVersStar())) {
            return List.of(tipoPendenzaRef(STAR_ID, STAR_LABEL));
        }
        Set<Long> ids = utenzaTipoVersamentoRepository.findByIdUtenza(utenza.getId()).stream()
                .map(UtenzaTipoVersamento::getIdTipoVersamento)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return List.of();
        }
        return tipoVersamentoRepository.findAllById(ids).stream()
                .map(UtenzaAssociazioniMapper::toTipoPendenzaRef)
                .toList();
    }

    public List<RuoloRef> buildRuoli(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(RuoloRef::new)
                .toList();
    }

    public List<Acl> buildAcl(long idUtenza) {
        return aclRepository.findByIdUtenza(idUtenza).stream()
                .map(UtenzaAssociazioniMapper::toAcl)
                .filter(Objects::nonNull)
                .toList();
    }

    public static TipoPendenzaRef tipoPendenzaRef(String id, String descrizione) {
        TipoPendenzaRef ref = new TipoPendenzaRef(id);
        ref.setDescrizione(descrizione);
        return ref;
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

    private static Acl toAcl(it.govpay.console.entity.Acl entity) {
        AclServizio servizio = SERVIZIO_LOOKUP.get(entity.getServizio());
        if (servizio == null) {
            return null; // servizio fuori dall'enum noto → skip
        }
        Acl acl = new Acl();
        acl.setServizio(servizio);
        acl.setAutorizzazioni(DirittiCodec.parse(entity.getDiritti()));
        acl.setRuolo(entity.getRuolo());
        return acl;
    }
}
