package it.govpay.console.utenza;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import it.govpay.console.common.DirittiCodec;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.UtenzaTipoVersamento;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.RuoloRef;
import it.govpay.console.model.TipoPendenzaRef;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.UnprocessableEntityException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Logica di scrittura delle associazioni di una utenza (domini, tipi pendenza,
 * ruoli, ACL) condivisa da applicazioni ({@code /applicazioni}) e operatori
 * ({@code /operatori}). Risolve i riferimenti (404 se inesistenti), valida i
 * ruoli contro il catalogo e gestisce le righe figlie {@code utenze_domini},
 * {@code utenze_tipo_vers}, {@code acl} con strategia delete-and-reinsert.
 *
 * <p>Non gestisce il flag {@code trusted}/{@code autodeterminazione}, che e'
 * specifico delle applicazioni: il chiamante che lo supporta filtra il valore
 * {@code autodeterminazione} prima di invocare {@link #resolveTipiPendenza}.
 */
@Component
public class UtenzaAssociazioniWriter {

    private final DominioRepository dominioRepository;
    private final TipoVersamentoRepository tipoVersamentoRepository;
    private final AclRepository aclRepository;
    private final UtenzaDominioRepository utenzaDominioRepository;
    private final UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public UtenzaAssociazioniWriter(DominioRepository dominioRepository,
                                    TipoVersamentoRepository tipoVersamentoRepository,
                                    AclRepository aclRepository,
                                    UtenzaDominioRepository utenzaDominioRepository,
                                    UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository) {
        this.dominioRepository = dominioRepository;
        this.tipoVersamentoRepository = tipoVersamentoRepository;
        this.aclRepository = aclRepository;
        this.utenzaDominioRepository = utenzaDominioRepository;
        this.utenzaTipoVersamentoRepository = utenzaTipoVersamentoRepository;
    }

    public DominiResolution resolveDomini(List<DominioRef> domini) {
        boolean star = false;
        List<Long> ids = new ArrayList<>();
        if (domini != null) {
            for (DominioRef ref : domini) {
                String id = ref == null ? null : ref.getIdDominio();
                if (id == null || id.isBlank()) {
                    throw new UnprocessableEntityException("Ogni elemento di 'domini' deve avere 'idDominio' valorizzato.");
                }
                if (UtenzaAssociazioniMapper.STAR_ID.equals(id)) {
                    star = true;
                    continue;
                }
                Dominio d = dominioRepository.findByCodDominio(id)
                        .orElseThrow(() -> new NotFoundException("Dominio riferito non trovato: " + id));
                ids.add(d.getId());
            }
        }
        if (star) {
            ids.clear();
        }
        return new DominiResolution(star, ids);
    }

    public TipiResolution resolveTipiPendenza(List<TipoPendenzaRef> tipiPendenza) {
        boolean star = false;
        List<Long> ids = new ArrayList<>();
        if (tipiPendenza != null) {
            for (TipoPendenzaRef ref : tipiPendenza) {
                String id = ref == null ? null : ref.getIdTipoPendenza();
                if (id == null || id.isBlank()) {
                    throw new UnprocessableEntityException(
                            "Ogni elemento di 'tipiPendenza' deve avere 'idTipoPendenza' valorizzato.");
                }
                if (UtenzaAssociazioniMapper.STAR_ID.equals(id)) {
                    star = true;
                    continue;
                }
                TipoVersamento t = tipoVersamentoRepository.findByCodTipoVersamento(id)
                        .orElseThrow(() -> new NotFoundException("Tipo pendenza riferito non trovato: " + id));
                ids.add(t.getId());
            }
        }
        if (star) {
            ids.clear();
        }
        return new TipiResolution(star, ids);
    }

    public String validateRuoliToCsv(List<RuoloRef> ruoli) {
        if (ruoli == null || ruoli.isEmpty()) {
            return null;
        }
        Set<String> catalogo = new HashSet<>(aclRepository.findRuoliCatalogo());
        List<String> ids = new ArrayList<>();
        for (RuoloRef ref : ruoli) {
            String id = ref == null ? null : ref.getId();
            if (id == null || id.isBlank()) {
                throw new UnprocessableEntityException("Ogni elemento di 'ruoli' deve avere 'id' valorizzato.");
            }
            String trimmed = id.trim();
            if (!catalogo.contains(trimmed)) {
                throw new NotFoundException("Ruolo riferito non trovato: " + trimmed);
            }
            ids.add(trimmed);
        }
        return String.join(",", ids);
    }

    public List<Acl> buildAclEntities(List<it.govpay.console.model.Acl> aclList, long idUtenza) {
        List<Acl> out = new ArrayList<>();
        if (aclList == null) {
            return out;
        }
        for (it.govpay.console.model.Acl a : aclList) {
            if (a.getServizio() == null) {
                throw new UnprocessableEntityException("Ogni elemento di 'acl' deve avere 'servizio' valorizzato.");
            }
            if (a.getAutorizzazioni() == null || a.getAutorizzazioni().isEmpty()) {
                throw new UnprocessableEntityException(
                        "Ogni elemento di 'acl' deve avere almeno un'autorizzazione (R e/o W).");
            }
            Acl entity = new Acl();
            entity.setServizio(a.getServizio().getValue());
            entity.setDiritti(DirittiCodec.serialize(a.getAutorizzazioni()));
            entity.setRuolo(a.getRuolo());
            entity.setIdUtenza(idUtenza);
            out.add(entity);
        }
        return out;
    }

    public void writeChildren(long idUtenza, DominiResolution dom, TipiResolution tipi, List<Acl> aclEntities) {
        if (!aclEntities.isEmpty()) {
            aclRepository.saveAll(aclEntities);
        }
        if (!dom.star() && !dom.idDomini().isEmpty()) {
            List<UtenzaDominio> rows = new ArrayList<>();
            for (Long idDominio : dom.idDomini()) {
                UtenzaDominio ud = new UtenzaDominio();
                ud.setIdUtenza(idUtenza);
                ud.setIdDominio(idDominio);
                ud.setIdUo(null);
                rows.add(ud);
            }
            utenzaDominioRepository.saveAll(rows);
        }
        if (!tipi.star() && !tipi.idTipiVersamento().isEmpty()) {
            List<UtenzaTipoVersamento> rows = new ArrayList<>();
            for (Long idTipoVersamento : tipi.idTipiVersamento()) {
                UtenzaTipoVersamento utv = new UtenzaTipoVersamento();
                utv.setIdUtenza(idUtenza);
                utv.setIdTipoVersamento(idTipoVersamento);
                rows.add(utv);
            }
            utenzaTipoVersamentoRepository.saveAll(rows);
        }
    }

    /** Cancella le righe figlie dell'utenza e forza il flush (ordine delete→insert nel replace). */
    public void deleteChildrenAndFlush(long idUtenza) {
        aclRepository.deleteByIdUtenza(idUtenza);
        utenzaDominioRepository.deleteByIdUtenza(idUtenza);
        utenzaTipoVersamentoRepository.deleteByIdUtenza(idUtenza);
        entityManager.flush();
    }

    public record DominiResolution(boolean star, List<Long> idDomini) {
    }

    public record TipiResolution(boolean star, List<Long> idTipiVersamento) {
    }
}
