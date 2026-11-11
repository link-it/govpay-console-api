package it.govpay.console.ricevuta;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.openapitools.jackson.nullable.JsonNullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.govpay.console.entity.Fr;
import it.govpay.console.entity.Incasso;
import it.govpay.console.entity.Pagamento;
import it.govpay.console.entity.Rendicontazione;
import it.govpay.console.model.Link;
import it.govpay.console.model.RigaRendicontazione;
import it.govpay.console.model.RigaRendicontazioneLinks;
import it.govpay.console.model.RiconciliazioneVoce;
import it.govpay.console.model.RiconciliazioneVoceLinks;
import it.govpay.console.model.StatoRendicontazioneVoce;
import it.govpay.console.model.StatoRigaRendicontazione;
import it.govpay.console.model.VoceRicevuta;
import it.govpay.console.rendicontazione.AnomalieRendicontazioneParser;
import it.govpay.console.rendicontazione.EsitoRendicontazioneMapper;

/**
 * Mapper {@link Pagamento}/{@link Rendicontazione}/{@link Incasso} →
 * {@link VoceRicevuta}. Le liste di {@link Rendicontazione} passate in
 * ingresso sono gia' filtrate dal chiamante su {@code fr.obsoleto = false}:
 * {@code statoRendicontazione} e {@code anomalia}/{@code anomalie} sono
 * calcolati su tutte le righe passate, {@code rendicontazioni[]} le espone
 * tutte senza selezionarne una.
 */
@Component
public class VoceRicevutaMapper {

    private static final Logger log = LoggerFactory.getLogger(VoceRicevutaMapper.class);

    /**
     * Solo {@code ANOMALA} e' un'anomalia: {@code ALTRO_INTERMEDIARIO} e'
     * l'importo di uno IUV gestito da un altro intermediario, situazione normale.
     */
    private static final String STATO_RIGA_ANOMALA = "ANOMALA";

    /**
     * {@code Ricevuta.voci[]}: una voce per pagamento, nell'ordine dato da
     * {@code pagamenti}.
     */
    public List<VoceRicevuta> toVoci(List<Pagamento> pagamenti,
                                     Map<Long, List<Rendicontazione>> rendicontazioniByIdPagamento,
                                     Map<Long, Incasso> incassiById) {
        return pagamenti.stream()
                .map(p -> toVoce(p,
                        rendicontazioniByIdPagamento.getOrDefault(p.getId(), List.of()),
                        p.getIdIncasso() != null ? incassiById.get(p.getIdIncasso()) : null))
                .toList();
    }

    private VoceRicevuta toVoce(Pagamento pagamento, List<Rendicontazione> correnti, Incasso incasso) {
        VoceRicevuta voce = new VoceRicevuta();
        voce.setIndice(pagamento.getIndiceDati());
        voce.setStatoRendicontazione(statoRendicontazione(pagamento, correnti));
        voce.setAnomalia(correnti.stream().anyMatch(VoceRicevutaMapper::anomala));
        voce.setAnomalie(correnti.stream()
                .flatMap(r -> AnomalieRendicontazioneParser.parse(r.getAnomalie()).stream())
                .toList());
        voce.setRendicontazioni(correnti.stream().map(this::toRiga).toList());
        voce.setRiconciliazione(JsonNullable.of(incasso != null ? toRiconciliazione(incasso) : null));
        return voce;
    }

    /**
     * Precedenza: {@code Pagamento.idIncasso} popolato -> {@code
     * RICONCILIATA} (anche a {@code correnti} vuoto); almeno una riga
     * corrente -> {@code RENDICONTATA}; altrimenti {@code DA_RENDICONTARE}.
     */
    private static StatoRendicontazioneVoce statoRendicontazione(Pagamento pagamento, List<Rendicontazione> correnti) {
        if (pagamento.getIdIncasso() != null) {
            return StatoRendicontazioneVoce.RICONCILIATA;
        }
        if (!correnti.isEmpty()) {
            return StatoRendicontazioneVoce.RENDICONTATA;
        }
        return StatoRendicontazioneVoce.DA_RENDICONTARE;
    }

    private static boolean anomala(Rendicontazione r) {
        return STATO_RIGA_ANOMALA.equals(r.getStato());
    }

    private RigaRendicontazione toRiga(Rendicontazione r) {
        RigaRendicontazione riga = new RigaRendicontazione();
        riga.setIur(r.getIur());
        riga.setDataRendicontazione(r.getData());
        riga.setEsito(JsonNullable.of(EsitoRendicontazioneMapper.fromCodice(r.getEsito())));
        riga.setImporto(r.getImportoPagato());
        riga.setStato(statoRiga(r.getStato()));
        riga.setAnomalie(AnomalieRendicontazioneParser.parse(r.getAnomalie()));
        Fr fr = r.getFr();
        if (fr != null) {
            riga.setIdFlusso(fr.getCodFlusso());
            riga.setIdPsp(fr.getCodPsp());
            riga.setRevisione(fr.getRevisione());
            riga.setDataFlusso(fr.getDataOraFlusso());
            RigaRendicontazioneLinks links = new RigaRendicontazioneLinks();
            links.setFlussoRendicontazione(new Link("/flussi-rendicontazione/" + segment(fr.getCodDominio())
                    + "/" + segment(fr.getCodFlusso()) + "/" + segment(fr.getCodPsp()) + "/" + fr.getRevisione()));
            riga.setLinks(links);
        }
        return riga;
    }

    /** Codifica un singolo segmento di path. */
    private static String segment(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static StatoRigaRendicontazione statoRiga(String stato) {
        if (stato == null) {
            return null;
        }
        try {
            return StatoRigaRendicontazione.valueOf(stato);
        } catch (IllegalArgumentException e) {
            log.warn("Stato rendicontazione non riconosciuto: {}", stato);
            return null;
        }
    }

    private static RiconciliazioneVoce toRiconciliazione(Incasso incasso) {
        RiconciliazioneVoce rv = new RiconciliazioneVoce();
        rv.setId(incasso.getIdentificativo());
        rv.setData(incasso.getDataOraIncasso());
        rv.setTrn(incasso.getTrn());
        rv.setSct(incasso.getSct());
        RiconciliazioneVoceLinks links = new RiconciliazioneVoceLinks();
        links.setRiconciliazione(new Link("/riconciliazioni/" + segment(incasso.getCodDominio()) + "/"
                + segment(incasso.getIdentificativo())));
        rv.setLinks(links);
        return rv;
    }
}
