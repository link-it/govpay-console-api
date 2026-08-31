package it.govpay.console.tracciato;

import org.openapitools.jackson.nullable.JsonNullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operazione;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.AnnullamentoPendenza;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.EsitoOperazionePendenza;
import it.govpay.console.model.NuovaPendenzaTracciato;
import it.govpay.console.model.OperazionePendenza;
import it.govpay.console.model.OperazionePendenzaAllOfRichiesta;
import it.govpay.console.model.OperazionePendenzaSummary;
import it.govpay.console.model.OperazionePendenzaSummaryLinks;
import it.govpay.console.model.StatoOperazionePendenza;
import it.govpay.console.model.TipoOperazionePendenza;
import it.govpay.console.model.Link;
import it.govpay.console.soggetto.SoggettoMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Mappa {@link Operazione} sugli schemi V2 {@code OperazionePendenzaSummary}/{@code OperazionePendenza}. */
@Component
public class OperazionePendenzaMapper {

    private static final Logger log = LoggerFactory.getLogger(OperazionePendenzaMapper.class);

    private final ObjectMapper objectMapper;
    private final SoggettoMapper soggettoMapper;

    public OperazionePendenzaMapper(ObjectMapper objectMapper, SoggettoMapper soggettoMapper) {
        this.objectMapper = objectMapper;
        this.soggettoMapper = soggettoMapper;
    }

    public OperazionePendenzaSummary toSummary(Operazione op) {
        OperazionePendenzaSummary dto = new OperazionePendenzaSummary(
                op.getLineaElaborazione(),
                TipoOperazioneMapping.map(op.getTipoOperazione()),
                StatoOperazioneMapping.map(op.getStato()));
        dto.setDescrizioneStato(JsonNullable.of(op.getDettaglioEsito()));
        dto.setIdentificativoPendenza(JsonNullable.of(op.getCodVersamentoEnte()));
        Versamento versamento = op.getVersamento();
        dto.setNumeroAvviso(JsonNullable.of(versamento != null ? versamento.getNumeroAvviso() : null));
        dto.setIdDominio(JsonNullable.of(op.getCodDominio()));
        dto.setLinks(new OperazionePendenzaSummaryLinks()
                .self(new Link("/pendenze/tracciati/" + op.getTracciato().getId()
                        + "/operazioni/" + op.getLineaElaborazione())));
        return dto;
    }

    public OperazionePendenza toDetail(Operazione op) {
        OperazionePendenzaSummary summary = toSummary(op);
        OperazionePendenza dto = new OperazionePendenza(
                summary.getNumero(), summary.getTipoOperazione(), summary.getStato());
        dto.setDescrizioneStato(summary.getDescrizioneStato());
        dto.setIdentificativoPendenza(summary.getIdentificativoPendenza());
        dto.setNumeroAvviso(summary.getNumeroAvviso());
        dto.setIdDominio(summary.getIdDominio());
        dto.setLinks(summary.getLinks());

        Dominio dominio = op.getTracciato().getDominio();
        dto.setEnteCreditore(new DominioRef(dominio.getCodDominio()).ragioneSociale(dominio.getRagioneSociale()));

        Versamento versamento = op.getVersamento();
        if (versamento != null) {
            dto.setSoggettoPagatore(soggettoMapper.toSoggetto(versamento));
        }
        if (op.getApplicazione() != null) {
            dto.setApplicazione(JsonNullable.of(op.getApplicazione().getCodApplicazione()));
        }

        dto.setRichiesta(JsonNullable.of(parseRichiesta(op)));
        dto.setRisposta(JsonNullable.of(parseRisposta(op)));
        return dto;
    }

    private OperazionePendenzaAllOfRichiesta parseRichiesta(Operazione op) {
        byte[] json = op.getDatiRichiesta();
        if (json == null || json.length == 0) {
            return null;
        }
        try {
            boolean annullamento = "DEL".equals(op.getTipoOperazione());
            return annullamento
                    ? objectMapper.readValue(json, AnnullamentoPendenza.class)
                    : objectMapper.readValue(json, NuovaPendenzaTracciato.class);
        } catch (JacksonException e) {
            log.warn("dati_richiesta non deserializzabile per operazione id={} tracciato={} numero={}",
                    op.getId(), op.getTracciato().getId(), op.getLineaElaborazione(), e);
            return null;
        }
    }

    private EsitoOperazionePendenza parseRisposta(Operazione op) {
        byte[] json = op.getDatiRisposta();
        if (json == null || json.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(json, EsitoOperazionePendenza.class);
        } catch (JacksonException e) {
            log.warn("dati_risposta non deserializzabile per operazione id={} tracciato={} numero={}",
                    op.getId(), op.getTracciato().getId(), op.getLineaElaborazione(), e);
            return null;
        }
    }
}
