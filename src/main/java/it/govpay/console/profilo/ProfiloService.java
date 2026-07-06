package it.govpay.console.profilo;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.common.DirittiCodec;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.Acl;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.Profilo;
import it.govpay.console.model.RuoloRef;
import it.govpay.console.model.TipoPendenzaRef;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;

/**
 * Costruisce il {@link Profilo} per l'utenza autenticata corrente:
 * <ul>
 *   <li>nome operatore (o principal se non c'e' operatore associato);</li>
 *   <li>liste {@code domini} / {@code tipiPendenza} risolte dal data
 *       layer, con singolo placeholder {@code {id:"*", desc:"Tutti"}}
 *       quando l'utenza ha autorizzazione "star" (visibilita' totale);</li>
 *   <li>{@code ruoli} dal CSV {@code utenze.ruoli};</li>
 *   <li>{@code acl} appiattite per servizio + diritti R/W.</li>
 * </ul>
 *
 * <p>{@code autenticazione} non viene settato qui: e' compito del chiamante
 * che ha accesso all'{@code HttpServletRequest} (via {@code AuthTypeAccessor}).
 */
@Service
public class ProfiloService {

    static final String STAR_ID = "*";
    static final String STAR_LABEL = "Tutti";

    private static final Map<String, AclServizio> SERVIZIO_LOOKUP = Arrays.stream(AclServizio.values())
            .collect(Collectors.toMap(AclServizio::getValue, e -> e));

    private final CurrentOperatorService currentOperatorService;
    private final UtenzaRepository utenzaRepository;
    private final DominioRepository dominioRepository;
    private final UnitaOperativaRepository unitaOperativaRepository;
    private final TipoVersamentoRepository tipoVersamentoRepository;
    private final AclRepository aclRepository;

    public ProfiloService(CurrentOperatorService currentOperatorService,
                          UtenzaRepository utenzaRepository,
                          DominioRepository dominioRepository,
                          UnitaOperativaRepository unitaOperativaRepository,
                          TipoVersamentoRepository tipoVersamentoRepository,
                          AclRepository aclRepository) {
        this.currentOperatorService = currentOperatorService;
        this.utenzaRepository = utenzaRepository;
        this.dominioRepository = dominioRepository;
        this.unitaOperativaRepository = unitaOperativaRepository;
        this.tipoVersamentoRepository = tipoVersamentoRepository;
        this.aclRepository = aclRepository;
    }

    @Transactional(readOnly = true)
    public Profilo build() {
        OperatoreCorrente operatore = currentOperatorService.get();
        Utenza utenza = utenzaRepository.findByPrincipal(operatore.principal())
                .orElseThrow(() -> new IllegalStateException(
                        "Utenza non trovata per principal autenticato: " + operatore.principal()));
        Profilo profilo = new Profilo();
        profilo.setNome(operatore.nomeOperatore() != null ? operatore.nomeOperatore() : operatore.principal());
        profilo.setPrincipal(operatore.principal());
        profilo.setDomini(buildDomini(operatore));
        profilo.setTipiPendenza(buildTipiPendenza(operatore));
        profilo.setRuoli(buildRuoli(utenza));
        profilo.setAcl(buildAcl(operatore.idUtenza()));
        return profilo;
    }

    private List<DominioRef> buildDomini(OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            DominioRef placeholder = new DominioRef();
            placeholder.setIdDominio(STAR_ID);
            placeholder.setRagioneSociale(STAR_LABEL);
            return List.of(placeholder);
        }
        // Un operatore vede un dominio quando ha autorizzazione su tutto il
        // dominio (utenze_domini.id_uo IS NULL) OPPURE su almeno una delle
        // sue UO: bisogna unire i due insiemi per non perdere il dominio
        // padre quando l'autorizzazione esiste solo per UO.
        Set<Long> idDomini = new LinkedHashSet<>(operatore.idDominiInteri());
        if (!operatore.idUoVisibili().isEmpty()) {
            idDomini.addAll(unitaOperativaRepository.findDistinctDominioIdsByIdIn(operatore.idUoVisibili()));
        }
        if (idDomini.isEmpty()) {
            return List.of();
        }
        return dominioRepository.findAllById(idDomini).stream()
                .map(ProfiloService::toDominioRef)
                .toList();
    }

    private List<TipoPendenzaRef> buildTipiPendenza(OperatoreCorrente operatore) {
        if (operatore.tuttiITipiVersamento()) {
            TipoPendenzaRef placeholder = new TipoPendenzaRef();
            placeholder.setIdTipoPendenza(STAR_ID);
            placeholder.setDescrizione(STAR_LABEL);
            return List.of(placeholder);
        }
        if (operatore.idTipiVersamentoVisibili().isEmpty()) {
            return List.of();
        }
        return tipoVersamentoRepository.findAllById(operatore.idTipiVersamentoVisibili()).stream()
                .map(ProfiloService::toTipoPendenzaRef)
                .toList();
    }

    private static List<RuoloRef> buildRuoli(Utenza utenza) {
        String csv = utenza.getRuoli();
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ProfiloService::toRuoloRef)
                .toList();
    }

    private List<Acl> buildAcl(long idUtenza) {
        return aclRepository.findByIdUtenza(idUtenza).stream()
                .map(ProfiloService::toAcl)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static DominioRef toDominioRef(Dominio dominio) {
        DominioRef ref = new DominioRef();
        ref.setIdDominio(dominio.getCodDominio());
        ref.setRagioneSociale(dominio.getRagioneSociale());
        return ref;
    }

    private static TipoPendenzaRef toTipoPendenzaRef(TipoVersamento tipo) {
        TipoPendenzaRef ref = new TipoPendenzaRef();
        ref.setIdTipoPendenza(tipo.getCodTipoVersamento());
        ref.setDescrizione(tipo.getDescrizione());
        return ref;
    }

    private static RuoloRef toRuoloRef(String id) {
        RuoloRef ref = new RuoloRef();
        ref.setId(id);
        return ref;
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
