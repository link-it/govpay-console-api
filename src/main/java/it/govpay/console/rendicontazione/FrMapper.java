package it.govpay.console.rendicontazione;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Fr;
import it.govpay.console.model.FlussoRendicontazione;
import it.govpay.console.model.FlussoRendicontazioneLinks;
import it.govpay.console.model.FlussoRendicontazioneSummary;
import it.govpay.console.model.Link;
import it.govpay.console.model.StatoFlussoRendicontazione;

/**
 * Mapper {@link Fr} → {@link FlussoRendicontazioneSummary}. Lo stato "arricchito"
 * dell'API (che include {@code OBSOLETO}) è calcolato da {@code stato} raw (3
 * valori) + {@code obsoleto}: nessuna nuova colonna, vedi {@link FrSpecifications#statoEsatto}
 * per la stessa traduzione lato filtro.
 */
@Component
public class FrMapper {

    public FlussoRendicontazioneSummary toSummary(Fr fr) {
        FlussoRendicontazioneSummary s = new FlussoRendicontazioneSummary();
        s.setIdDominio(fr.getCodDominio());
        s.setIdFlusso(fr.getCodFlusso());
        s.setIdPsp(fr.getCodPsp());
        s.setRevisione(fr.getRevisione() != null ? fr.getRevisione() : 1L);
        s.setDataOraFlusso(fr.getDataOraFlusso());
        s.setDataAcquisizione(fr.getDataAcquisizione());
        if (fr.getDataRegolamento() != null) {
            s.setDataRegolamento(fr.getDataRegolamento().toLocalDate());
        }
        s.setSctBonifico(fr.getIur());
        s.setStato(statoArricchito(fr));
        s.setDescrizioneStato(fr.getDescrizioneStato());
        s.setNumeroPagamenti(fr.getNumeroPagamenti());
        s.setImportoTotale(fr.getImportoTotalePagamenti());
        return s;
    }

    /**
     * Dettaglio canonico: gli stessi campi della summary + {@code dataInizio}/
     * {@code dataFine} (finestra temporale, mai calcolata nella lista) + {@code _links}.
     * Niente {@code xml} inline: si ottiene sullo stesso path via content negotiation.
     */
    public FlussoRendicontazione toDetail(Fr fr, FrPeriodo periodo) {
        FlussoRendicontazione d = new FlussoRendicontazione();
        d.setIdDominio(fr.getCodDominio());
        d.setIdFlusso(fr.getCodFlusso());
        d.setIdPsp(fr.getCodPsp());
        d.setRevisione(fr.getRevisione() != null ? fr.getRevisione() : 1L);
        d.setDataOraFlusso(fr.getDataOraFlusso());
        d.setDataAcquisizione(fr.getDataAcquisizione());
        if (fr.getDataRegolamento() != null) {
            d.setDataRegolamento(fr.getDataRegolamento().toLocalDate());
        }
        d.setSctBonifico(fr.getIur());
        d.setStato(statoArricchito(fr));
        d.setDescrizioneStato(fr.getDescrizioneStato());
        d.setNumeroPagamenti(fr.getNumeroPagamenti());
        d.setImportoTotale(fr.getImportoTotalePagamenti());
        if (periodo != null) {
            d.setDataInizio(periodo.dataInizio());
            d.setDataFine(periodo.dataFine());
        }
        d.setLinks(new FlussoRendicontazioneLinks(new Link("/domini/" + fr.getCodDominio())));
        return d;
    }

    private static StatoFlussoRendicontazione statoArricchito(Fr fr) {
        if (fr.isObsoleto()) {
            return StatoFlussoRendicontazione.OBSOLETO;
        }
        if (fr.getStato() == null) {
            return null;
        }
        return switch (fr.getStato()) {
            case "ACCETTATA" -> StatoFlussoRendicontazione.ACQUISITO;
            case "ANOMALA" -> StatoFlussoRendicontazione.ANOMALO;
            case "RIFIUTATA" -> StatoFlussoRendicontazione.RIFIUTATO;
            default -> null;
        };
    }
}
