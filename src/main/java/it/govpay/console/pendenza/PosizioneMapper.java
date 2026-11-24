package it.govpay.console.pendenza;

import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.console.model.DebitorePosizione;
import it.govpay.console.model.OpzionePagamentoPosizione;
import it.govpay.console.model.Link;
import it.govpay.console.model.PendenzaSummary;
import it.govpay.console.model.PosizioneDebitoria;
import it.govpay.console.model.PosizioneDebitoriaLinks;
import it.govpay.console.soggetto.SoggettoMapper;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.PdDebitore;

/** Mapper della posizione debitoria del modello v3 verso il contratto. */
@Component
class PosizioneMapper {

    private final SoggettoMapper soggettoMapper;

    PosizioneMapper(SoggettoMapper soggettoMapper) {
        this.soggettoMapper = soggettoMapper;
    }

    PosizioneDebitoria toPosizione(String idA2A, String idPosizioneDebitoria,
                                   it.govpay.pendenze.entity.PosizioneDebitoria posizione,
                                   PendenzaAnagrafiche.Pagina pagina, List<DebitorePosizione> debitori) {
        return new PosizioneDebitoria(
                idA2A,
                idPosizioneDebitoria,
                PendenzaMapper.mapDominio(pagina.domini().get(posizione.getIdDominio())),
                posizione.getDescrizione(),
                posizione.getDataCreazione(),
                posizione.getDataUltimoAggiornamento(),
                debitori,
                new PosizioneDebitoriaLinks(new Link("/posizioni-debitorie/" + idA2A + "/" + idPosizioneDebitoria + "/avviso")))
                .unitaOperativa(PendenzaMapper.mapUnitaOperativa(pagina.unitaOperative().get(posizione.getIdUnitaOperativa())))
                .dataPubblicazione(posizione.getDataPubblicazione());
    }

    DebitorePosizione toDebitore(PdDebitore pdDebitore, List<OpzionePagamentoPosizione> opzioni) {
        return new DebitorePosizione(soggettoMapper.toSoggetto(pdDebitore.getDebitore()),
                pdDebitore.isRimosso(), opzioni);
    }

    /** Le notifiche SEND si leggono dall'opzione intera, anche se alcune pendenze non sono visibili. */
    OpzionePagamentoPosizione toOpzione(OpzionePagamento opzione, List<PendenzaSummary> pendenze) {
        return new OpzionePagamentoPosizione(
                opzione.getIdOpzionePagamento(),
                OpzionePagamentoPosizione.TipologiaEnum.valueOf(opzione.getTipologia().name()),
                OpzionePagamentoPosizione.StatoEnum.valueOf(opzione.getStato().name()),
                opzione.isSendAbilitato(),
                pendenze)
                .giorni(opzione.getGiorni())
                .dataInizioValidita(opzione.getDataInizioValidita())
                .dataScadenza(opzione.getDataScadenza())
                .sendNav(opzione.getSendNav());
    }
}
