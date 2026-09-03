package it.govpay.console.ricevuta.recupero;

import java.time.OffsetDateTime;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.RtRecupero;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.RtRecuperoRepository;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.VersamentoVisibilita;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Logica transazionale di {@code POST /ricevute/recuperi}: ACL, pre-flight
 * (issue #59 §H) e upsert della riga in {@code rt_recuperi}. Separata da
 * {@link RecuperoRicevutaService} (non transazionale) perché la riga va
 * **committata** prima di innescare il {@code /run} del batch (fatto dal
 * chiamante, dopo che questo metodo ritorna): la self-invocation di
 * {@code @Transactional} nella stessa classe non garantirebbe un commit reale
 * a quel punto, e il batch potrebbe partire senza trovare ancora nulla.
 */
@Service
public class RtRecuperoUpsertService {

    private final RptRepository rptRepository;
    private final RtRecuperoRepository rtRecuperoRepository;
    private final AclAuthorizer aclAuthorizer;

    @PersistenceContext
    private EntityManager entityManager;

    public RtRecuperoUpsertService(RptRepository rptRepository,
                                   RtRecuperoRepository rtRecuperoRepository,
                                   AclAuthorizer aclAuthorizer) {
        this.rptRepository = rptRepository;
        this.rtRecuperoRepository = rtRecuperoRepository;
        this.aclAuthorizer = aclAuthorizer;
    }

    @Transactional
    public RtRecupero upsert(String idDominio, String iuv, String idRicevuta, OperatoreCorrente operatore) {
        aclAuthorizer.requireScrittura(AclServizio.PAGAMENTI);

        Rpt rpt = rptRepository.findByDominioAndIuv(idDominio, iuv)
                .orElseThrow(() -> new NotFoundException(
                        "Pagamento non trovato: idDominio=" + idDominio + ", iuv=" + iuv + "."));
        if (!VersamentoVisibilita.isVisibile(rpt.getVersamento(), operatore)) {
            throw new AccessDeniedException(
                    "L'operatore '" + operatore.principal() + "' non ha visibilità sul dominio '" + idDominio + "'.");
        }
        if (rpt.getXmlRt() != null && rpt.getDataMsgRicevuta() != null) {
            throw new ConflictException(
                    "RT già acquisita per idDominio=" + idDominio + ", iuv=" + iuv + ": il recupero non serve.");
        }

        RtRecupero riga = rtRecuperoRepository
                .findFirstByCodDominioAndIuvAndIurAndEsitoIsNotNull(idDominio, iuv, idRicevuta)
                .orElseGet(() -> creaRiga(idDominio, iuv, idRicevuta));
        rtRecuperoRepository.riavviaRichiesta(riga.getId(), operatore.idOperatore());
        // riavviaRichiesta e' una query nativa: non aggiorna l'istanza gestita in
        // memoria (stesso motivo di IncassoRepository.touchDataOraIncasso). Senza
        // il refresh, un riuso di riga già marcata tornerebbe al chiamante con
        // l'esito/idOperatore pre-reset ancora in memoria.
        entityManager.refresh(riga);
        return riga;
    }

    private RtRecupero creaRiga(String idDominio, String iuv, String idRicevuta) {
        RtRecupero riga = new RtRecupero();
        riga.setCodDominio(idDominio);
        riga.setIuv(iuv);
        riga.setIur(idRicevuta);
        riga.setDataRichiesta(OffsetDateTime.now()); // placeholder, sovrascritto da riavviaRichiesta col clock del DB
        return rtRecuperoRepository.saveAndFlush(riga);
    }
}
