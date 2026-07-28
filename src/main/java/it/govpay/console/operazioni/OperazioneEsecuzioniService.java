package it.govpay.console.operazioni;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import it.govpay.common.batch.dto.ExecutionsPage;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.Esecuzione;
import it.govpay.console.model.ListEsecuzioni200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.model.StatoEsecuzione;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.NotFoundException;

@Service
public class OperazioneEsecuzioniService {

    private final OperazioniProperties operazioniProperties;
    private final OperazioneBatchClient client;
    private final OperazioneMapper mapper;
    private final AclAuthorizer aclAuthorizer;

    public OperazioneEsecuzioniService(OperazioniProperties operazioniProperties, OperazioneBatchClient client,
            OperazioneMapper mapper, AclAuthorizer aclAuthorizer) {
        this.operazioniProperties = operazioniProperties;
        this.client = client;
        this.mapper = mapper;
        this.aclAuthorizer = aclAuthorizer;
    }

    public ListEsecuzioni200Response list(String idOperazione, StatoEsecuzione stato,
            OffsetDateTime dataInizioMin, OffsetDateTime dataInizioMax,
            int page, int limit, boolean wantTotal) {
        OperazioneConfig config = findConfig(idOperazione);

        ListEsecuzioni200Response response = new ListEsecuzioni200Response();
        Pagination pagination = new Pagination(page, limit, false);

        if (config.getUrl() == null) {
            // Operazione locale (non batch-backed): nessuno storico di esecuzioni.
            response.setResults(List.of());
            response.setPagination(pagination);
            return response;
        }

        String statoCsv = OperazioneMapper.toBatchStatusCsv(stato);
        ExecutionsPage remotePage = client.listExecutions(config.getUrl(), statoCsv, dataInizioMin, dataInizioMax, page, limit, wantTotal);

        pagination.setHasNextPage(remotePage.isHasNextPage());
        pagination.setTotalResults(remotePage.getTotalResults());
        pagination.setTotalPages(remotePage.getTotalPages());

        response.setResults(remotePage.getResults().stream().map(mapper::toEsecuzioneSummary).toList());
        response.setPagination(pagination);
        return response;
    }

    public Esecuzione dettaglio(String idOperazione, String idEsecuzione) {
        OperazioneConfig config = findConfig(idOperazione);
        if (config.getUrl() == null) {
            throw new NotFoundException("Esecuzione '" + idEsecuzione + "' non trovata per l'operazione '" + idOperazione + "'.");
        }

        long id = parseId(idEsecuzione);
        LastExecutionInfo execution = client.getExecution(config.getUrl(), id);
        return mapper.toEsecuzione(idOperazione, execution);
    }

    /** Annullamento cooperativo reale via {@code DELETE {url}/executions/{id}} (govpay-common). */
    public void annullaEsecuzione(String idOperazione, String idEsecuzione) {
        aclAuthorizer.requireScrittura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);

        OperazioneConfig config = findConfig(idOperazione);
        if (config.getUrl() == null) {
            throw new NotFoundException("Esecuzione '" + idEsecuzione + "' non trovata per l'operazione '" + idOperazione + "'.");
        }

        long id = parseId(idEsecuzione);
        client.stopExecution(config.getUrl(), id);
    }

    private OperazioneConfig findConfig(String idOperazione) {
        return operazioniProperties.getCatalogo().stream()
                .filter(c -> idOperazione.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Operazione '" + idOperazione + "' non trovata nel catalogo."));
    }

    private static long parseId(String idEsecuzione) {
        try {
            return Long.parseLong(idEsecuzione);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Il campo 'idEsecuzione' deve essere un identificativo numerico.");
        }
    }
}
