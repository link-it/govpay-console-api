package it.govpay.console.riconciliazione;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Incasso;
import it.govpay.console.entity.Pagamento;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.Link;
import it.govpay.console.model.Riconciliazione;
import it.govpay.console.model.RiconciliazioneLinks;
import it.govpay.console.model.RiconciliazioneSummary;
import it.govpay.console.model.Riscossione;
import it.govpay.console.model.RiscossioneLinks;
import it.govpay.console.model.StatoRiconciliazione;
import it.govpay.console.model.StatoRiscossione;
import it.govpay.console.model.TipoRiscossione;

/**
 * Mapper {@link Incasso}/{@link Pagamento} → {@link RiconciliazioneSummary}/
 * {@link Riconciliazione}/{@link Riscossione}. Il {@code dominio} (con
 * {@code ragioneSociale}) è risolto dal chiamante e passato come mappa
 * {@code codDominio -> Dominio}, batch-fetched una volta per pagina invece di
 * una query per riga (vedi {@link RiconciliazioneSearchService}).
 */
@Component
public class RiconciliazioneMapper {

    public RiconciliazioneSummary toSummary(Incasso incasso, Map<String, Dominio> domini) {
        RiconciliazioneSummary s = new RiconciliazioneSummary();
        s.setId(incasso.getIdentificativo());
        s.setDominio(toDominioRef(incasso.getCodDominio(), domini));
        s.setImporto(incasso.getImporto());
        s.setData(incasso.getDataOraIncasso());
        s.setDataValuta(incasso.getDataValuta());
        s.setDataContabile(incasso.getDataContabile());
        s.setIbanAccredito(incasso.getIbanAccredito());
        s.setSct(incasso.getSct());
        s.setTrn(incasso.getTrn());
        s.setCausale(incasso.getCausale());
        s.setStato(statoArricchito(incasso.getStato()));
        s.setDescrizioneStato(incasso.getDescrizioneStato());
        if (incasso.getCodFlussoRendicontazione() != null) {
            s.setIdFlusso(incasso.getCodFlussoRendicontazione());
        } else {
            s.setIuv(incasso.getIuv());
        }
        return s;
    }

    /**
     * Dettaglio canonico: gli stessi campi della summary + {@code riscossioni[]}
     * + {@code _links}.
     */
    public Riconciliazione toDetail(Incasso incasso, Map<String, Dominio> domini, List<Pagamento> pagamenti,
                                    Map<Long, Rpt> rptById, Map<Long, SingoloVersamento> singoliVersamentiById) {
        Riconciliazione d = new Riconciliazione();
        d.setId(incasso.getIdentificativo());
        d.setDominio(toDominioRef(incasso.getCodDominio(), domini));
        d.setImporto(incasso.getImporto());
        d.setData(incasso.getDataOraIncasso());
        d.setDataValuta(incasso.getDataValuta());
        d.setDataContabile(incasso.getDataContabile());
        d.setIbanAccredito(incasso.getIbanAccredito());
        d.setSct(incasso.getSct());
        d.setTrn(incasso.getTrn());
        d.setCausale(incasso.getCausale());
        d.setStato(statoArricchito(incasso.getStato()));
        d.setDescrizioneStato(incasso.getDescrizioneStato());
        if (incasso.getCodFlussoRendicontazione() != null) {
            d.setIdFlusso(incasso.getCodFlussoRendicontazione());
        } else {
            d.setIuv(incasso.getIuv());
        }
        d.setRiscossioni(pagamenti.stream()
                .map(p -> toRiscossione(p, rptById, singoliVersamentiById))
                .toList());

        RiconciliazioneLinks links = new RiconciliazioneLinks(
                new Link("/riconciliazioni/" + incasso.getCodDominio() + "/" + incasso.getIdentificativo()),
                new Link("/domini/" + incasso.getCodDominio()));
        if (incasso.getIbanAccredito() != null) {
            links.setContoAccredito(new Link("/domini/" + incasso.getCodDominio()
                    + "/contiAccredito/" + incasso.getIbanAccredito()));
        }
        if (incasso.getCodFlussoRendicontazione() != null) {
            links.setFlussoRendicontazione(new Link("/flussi-rendicontazione/" + incasso.getCodDominio()
                    + "/" + incasso.getCodFlussoRendicontazione()));
        }
        d.setLinks(links);
        return d;
    }

    /**
     * {@code _links.pendenza} risolto via {@code Pagamento.idSingoloVersamento
     * -> SingoloVersamento -> Versamento -> (codVersamentoEnte, applicazione.codApplicazione)}
     * (pattern identico a {@code RicevutaMapper._links.pendenza}); {@code
     * _links.ricevuta} via {@code Pagamento.idRpt -> Rpt.ccp} (lo {@code idRicevuta}
     * del path e' il {@code ccp}, non lo {@code iur} — vedi {@code RicevutaMapper}).
     * Entrambe le mappe sono batch-fetched dal chiamante, nessuna query per riga.
     */
    private Riscossione toRiscossione(Pagamento p, Map<Long, Rpt> rptById,
                                      Map<Long, SingoloVersamento> singoliVersamentiById) {
        Riscossione r = new Riscossione();
        r.setIdDominio(p.getCodDominio());
        r.setIuv(p.getIuv());
        r.setIur(p.getIur());
        r.setIndice(p.getIndiceDati());
        r.setTipo(p.getTipo() != null ? TipoRiscossione.valueOf(p.getTipo()) : null);
        r.setImporto(p.getImportoPagato());
        r.setData(p.getDataPagamento());
        r.setCommissioni(p.getCommissioniPsp());
        r.setStato(p.getStato() != null ? StatoRiscossione.valueOf(p.getStato()) : null);

        RiscossioneLinks links = new RiscossioneLinks();
        SingoloVersamento sv = p.getIdSingoloVersamento() != null
                ? singoliVersamentiById.get(p.getIdSingoloVersamento())
                : null;
        if (sv != null) {
            String idA2A = sv.getVersamento().getApplicazione().getCodApplicazione();
            String idPendenza = sv.getVersamento().getCodVersamentoEnte();
            links.setPendenza(new Link("/pendenze/" + idA2A + "/" + idPendenza));
        }
        Rpt rpt = p.getIdRpt() != null ? rptById.get(p.getIdRpt()) : null;
        if (rpt != null) {
            links.setRicevuta(new Link("/ricevute/" + p.getCodDominio() + "/" + p.getIuv() + "/" + rpt.getCcp()));
        }
        r.setLinks(links);
        return r;
    }

    private static DominioRef toDominioRef(String codDominio, Map<String, Dominio> domini) {
        Dominio dominio = domini.get(codDominio);
        DominioRef ref = new DominioRef(codDominio);
        if (dominio != null) {
            ref.setRagioneSociale(dominio.getRagioneSociale());
        }
        return ref;
    }

    private static StatoRiconciliazione statoArricchito(String statoRaw) {
        if (statoRaw == null) {
            return null;
        }
        return switch (statoRaw) {
            case "NUOVO" -> StatoRiconciliazione.IN_ELABORAZIONE;
            case "ACQUISITO" -> StatoRiconciliazione.ACQUISITA;
            case "ERRORE" -> StatoRiconciliazione.ERRORE;
            default -> null;
        };
    }
}
