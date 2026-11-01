package it.govpay.console.profilo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.common.DirittiCodec;
import it.govpay.console.common.PreferenzeCodec;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.Utenza;
import it.govpay.console.intermediario.JsonPatchApplier;
import it.govpay.console.model.Acl;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.Profilo;
import it.govpay.console.model.RuoloRef;
import it.govpay.console.model.TipoPendenzaRef;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.security.AclEffettive;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.UnprocessableEntityException;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Costruisce il {@link Profilo} per l'utenza autenticata corrente:
 * <ul>
 *   <li>nome operatore (o principal se non c'e' operatore associato);</li>
 *   <li>liste {@code domini} / {@code tipiPendenza} risolte dal data
 *       layer, con singolo placeholder {@code {id:"*", desc:"Tutti"}}
 *       quando l'utenza ha autorizzazione "star" (visibilita' totale);</li>
 *   <li>{@code ruoli} dal CSV {@code utenze.ruoli};</li>
 *   <li>{@code acl} effettive (dirette e dei ruoli), una voce per servizio con
 *       l'unione dei diritti R/W.</li>
 * </ul>
 *
 * <p>{@code autenticazione} non viene settato qui: e' compito del chiamante
 * che ha accesso all'{@code HttpServletRequest} (via {@code AuthTypeAccessor}).
 */
@Service
public class ProfiloService {

    static final String STAR_ID = "*";
    static final String STAR_LABEL = "Tutti";

    private static final String CAMPO_PREFERENZE = "preferenze";
    private static final String AZIONE_AUDIT_PREFERENZE = "PROFILO_PREFERENZE_MODIFICA";

    private static final Map<String, AclServizio> SERVIZIO_LOOKUP = Arrays.stream(AclServizio.values())
            .collect(Collectors.toMap(AclServizio::getValue, e -> e));

    private final CurrentOperatorService currentOperatorService;
    private final UtenzaRepository utenzaRepository;
    private final DominioRepository dominioRepository;
    private final UnitaOperativaRepository unitaOperativaRepository;
    private final TipoVersamentoRepository tipoVersamentoRepository;
    private final AclEffettive aclEffettive;
    private final OperatoreRepository operatoreRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ProfiloService(CurrentOperatorService currentOperatorService,
                          UtenzaRepository utenzaRepository,
                          DominioRepository dominioRepository,
                          UnitaOperativaRepository unitaOperativaRepository,
                          TipoVersamentoRepository tipoVersamentoRepository,
                          AclEffettive aclEffettive,
                          OperatoreRepository operatoreRepository,
                          AuditService auditService,
                          ObjectMapper objectMapper) {
        this.currentOperatorService = currentOperatorService;
        this.utenzaRepository = utenzaRepository;
        this.dominioRepository = dominioRepository;
        this.unitaOperativaRepository = unitaOperativaRepository;
        this.tipoVersamentoRepository = tipoVersamentoRepository;
        this.aclEffettive = aclEffettive;
        this.operatoreRepository = operatoreRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Profilo build() {
        return buildProfilo();
    }

    /**
     * Corpo di {@link #build()} senza annotazione transazionale: {@link #patch}
     * lo invoca direttamente per restituire il profilo aggiornato senza passare
     * per il proxy Spring (una self-invocation di {@code build()} non
     * attraverserebbe comunque il proxy, e la transazione di scrittura gia'
     * aperta da {@code patch} e' quella corretta anche per la rilettura).
     */
    private Profilo buildProfilo() {
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
        profilo.setAcl(buildAcl(utenza));
        // Il campo del modello generato si autoinizializza a `new HashMap<>()` (non
        // null): settarlo esplicitamente a null e' necessario per farlo sparire dal
        // JSON (spring.jackson.default-property-inclusion=non_absent), non solo per
        // non valorizzarlo.
        profilo.setPreferenze(operatore.idOperatore() != null
                ? operatoreRepository.findById(operatore.idOperatore())
                        .map(op -> PreferenzeCodec.parse(op.getPreferenze(), objectMapper))
                        .orElse(null)
                : null);
        return profilo;
    }

    /**
     * {@code PATCH /profilo}: unico campo modificabile e' {@code preferenze}
     * (autenticazione = autorizzazione, nessuna ACL da verificare qui). Ogni
     * operazione del documento deve avere {@code path} (e, per move/copy,
     * anche {@code from}) uguale a {@code /preferenze}: qualunque altro
     * campo e' fuori scope -&gt; 403. Un'utenza senza operatore associato non
     * ha un posto dove salvare le preferenze -&gt; 422.
     */
    @Transactional
    public Profilo patch(List<JsonPatchOperation> operations, HttpServletRequest request) {
        checkScopePreferenze(operations);

        OperatoreCorrente operatoreCorrente = currentOperatorService.get();
        if (operatoreCorrente.idOperatore() == null) {
            throw new UnprocessableEntityException(
                    "Il profilo dell'utenza autenticata non e' associato a un operatore: "
                            + "le preferenze non possono essere modificate.");
        }
        Operatore op = operatoreRepository.findById(operatoreCorrente.idOperatore())
                .orElseThrow(() -> new IllegalStateException(
                        "Operatore non trovato per id: " + operatoreCorrente.idOperatore()));

        ObjectNode current = objectMapper.createObjectNode();
        current.set(CAMPO_PREFERENZE,
                objectMapper.valueToTree(PreferenzeCodec.parse(op.getPreferenze(), objectMapper)));
        ObjectNode patched = JsonPatchApplier.apply(current, operations, objectMapper);

        // Chiave assente (es. dopo una 'remove') -> azzeramento legittimo. Chiave
        // presente ma non un oggetto (stringa/numero/array/null esplicito) -> 400:
        // 'preferenze' e' vincolato a un oggetto JSON, un valore diverso non deve
        // ne' rompere la convertValue ne' essere silenziosamente accettato come
        // azzeramento.
        Map<String, Object> preferenze = null;
        if (patched.has(CAMPO_PREFERENZE)) {
            JsonNode preferenzeNode = patched.get(CAMPO_PREFERENZE);
            if (!preferenzeNode.isObject()) {
                throw new BadRequestException(
                        "Il campo 'preferenze' deve essere un oggetto JSON.", CAMPO_PREFERENZE);
            }
            preferenze = objectMapper.convertValue(preferenzeNode, new TypeReference<Map<String, Object>>() { });
        }
        op.setPreferenze(PreferenzeCodec.serialize(preferenze, objectMapper));
        operatoreRepository.save(op);

        auditService.registra(AZIONE_AUDIT_PREFERENZE, op.getId(), new HashMap<>(), operatoreCorrente, request);

        return buildProfilo();
    }

    private static void checkScopePreferenze(List<JsonPatchOperation> operations) {
        for (JsonPatchOperation operation : operations) {
            checkCampoConsentito(operation.getPath());
            if (operation.getOp() == JsonPatchOperation.OpEnum.MOVE
                    || operation.getOp() == JsonPatchOperation.OpEnum.COPY) {
                checkCampoConsentito(operation.getFrom());
            }
        }
    }

    private static void checkCampoConsentito(String pointer) {
        String consentito = "/" + CAMPO_PREFERENZE;
        if (!consentito.equals(pointer)) {
            throw new AccessDeniedException(
                    "Il campo '" + pointer + "' non e' modificabile tramite PATCH /profilo: "
                            + "solo '" + consentito + "' e' consentito.");
        }
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

    /**
     * Come {@code Utenza.getAclsProfilo()} del core: ACL dirette e dei ruoli accorpate in
     * una voce per servizio, nell'ordine dell'enum. A differenza del core, che tiene la
     * voce con piu' diritti, i diritti sono uniti: {@code R} da un ruolo e {@code W} da
     * un altro danno {@code RW}, come in {@code AclAuthorizer}. {@code ruolo} e'
     * valorizzato solo se la voce deriva da un unico ruolo.
     */
    private List<Acl> buildAcl(Utenza utenza) {
        Map<AclServizio, List<it.govpay.console.entity.Acl>> perServizio = new EnumMap<>(AclServizio.class);
        for (it.govpay.console.entity.Acl entity : aclEffettive.di(utenza)) {
            AclServizio servizio = SERVIZIO_LOOKUP.get(entity.getServizio());
            if (servizio != null) { // servizio fuori dall'enum noto → skip
                perServizio.computeIfAbsent(servizio, k -> new ArrayList<>()).add(entity);
            }
        }
        return perServizio.entrySet().stream()
                .map(e -> toAcl(e.getKey(), e.getValue()))
                .filter(Objects::nonNull)
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

    private static Acl toAcl(AclServizio servizio, List<it.govpay.console.entity.Acl> entities) {
        Set<Acl.AutorizzazioniEnum> diritti = EnumSet.noneOf(Acl.AutorizzazioniEnum.class);
        Set<String> ruoli = new HashSet<>();
        for (it.govpay.console.entity.Acl entity : entities) {
            diritti.addAll(DirittiCodec.parse(entity.getDiritti()));
            ruoli.add(entity.getRuolo());
        }
        if (diritti.isEmpty()) {
            return null; // nessun diritto R/W → voce non valida per lo schema (minItems 1)
        }
        Acl acl = new Acl();
        acl.setServizio(servizio);
        acl.setAutorizzazioni(new ArrayList<>(diritti));
        acl.setRuolo(ruoli.size() == 1 ? ruoli.iterator().next() : null);
        return acl;
    }
}
