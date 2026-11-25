package it.govpay.console.pendenza;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import it.govpay.console.common.CausaleVersamentoDecoder;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.OpzionePagamentoRef;
import it.govpay.console.model.Pendenza;
import it.govpay.console.model.PendenzaExpand;
import it.govpay.console.model.PendenzaSummary;
import it.govpay.console.model.PosizioneDebitoriaRef;
import it.govpay.console.model.RiferimentoDebitore;
import it.govpay.console.model.StatoPendenza;
import it.govpay.console.model.StatoVocePendenza;
import it.govpay.console.model.TipoContabilita;
import it.govpay.console.model.TipoPendenzaRef;
import it.govpay.console.model.UnitaOperativaRef;
import it.govpay.console.model.VocePendenza;
import it.govpay.console.model.VocePendenzaBolloTelematico;
import it.govpay.console.model.VocePendenzaDettaglio;
import it.govpay.console.model.VocePendenzaEntrataAnagrafica;
import it.govpay.console.model.VocePendenzaIncassoDiretto;
import it.govpay.pendenze.entity.Debitore;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.PosizioneDebitoria;
import it.govpay.pendenze.identificativo.IdentificatorePosizioneDebitoria;
import it.govpay.pendenze.model.DettaglioContabileConverter;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;

@Component
public class PendenzaMapper {

    /** Codice della unita' operativa che rappresenta l'ente creditore stesso. */
    static final String COD_UNITA_OPERATIVA_EC = "EC";

    private final Clock clock;

    public PendenzaMapper(Clock clock) {
        this.clock = clock;
    }

    /**
     * Riga della lista per una pendenza del modello v3, con opzione di pagamento e posizione
     * gia' caricate.
     *
     * @param idA2A    codice dell'applicazione della pendenza
     * @param contesto anagrafiche di dominio, tipo pendenza e unita' operativa
     */
    public PendenzaSummary toSummary(it.govpay.pendenze.entity.Pendenza p, String idA2A,
                                     PendenzaAnagrafiche.Contesto contesto) {
        if (p == null) {
            return null;
        }
        OpzionePagamento opzione = p.getOpzionePagamento();
        PosizioneDebitoria posizione = opzione.getPosizioneDebitoria();
        return new PendenzaSummary(
                idA2A,
                p.getIdPendenza(),
                mapStato(p.getStato(), p.getDataScadenzaAvviso(), posizione),
                mapDominio(contesto.dominio()),
                mapTipoPendenza(contesto.tipoVersamento()),
                mapDebitore(opzione),
                mapOpzione(opzione, p.getNumeroRata()),
                mapPosizione(idA2A, contesto.dominio(), posizione),
                p.getImporto(),
                CausaleVersamentoDecoder.decodeSimple(p.getCausaleVersamento()),
                p.getDataCreazione(),
                p.getDataUltimoAggiornamento())
                .direzione(p.getDirezione())
                .divisione(p.getDivisione())
                .unitaOperativa(mapUnitaOperativa(contesto.unitaOperativa()))
                .numeroAvviso(p.getNumeroAvviso())
                .iuvAvviso(p.getIuv())
                .dataScadenza(p.getDataScadenzaAvviso())
                .dataValidita(toLocalDate(p.getDataValidita()))
                .dataUltimaModificaAca(p.getDataUltimaModificaAca())
                .dataUltimaComunicazioneAca(p.getDataUltimaComunicazioneAca());
    }

    /**
     * Stato di una pendenza del modello v3. {@code NON_ESEGUITO} e' l'unico stato che si
     * deriva: diventa {@link StatoPendenza#NON_PUBBLICATA} se la posizione ha una data di
     * pubblicazione futura (prevale), altrimenti {@link StatoPendenza#SCADUTO} se la
     * scadenza e' passata.
     */
    StatoPendenza mapStato(it.govpay.pendenze.model.StatoPendenza stato, OffsetDateTime dataScadenza,
                           PosizioneDebitoria posizione) {
        if (stato == null) {
            return null;
        }
        return switch (stato) {
            case NON_ESEGUITO -> {
                if (posizione != null && !posizione.isPubblicata(LocalDate.now(clock))) {
                    yield StatoPendenza.NON_PUBBLICATA;
                }
                if (dataScadenza != null && dataScadenza.isBefore(OffsetDateTime.now(clock))) {
                    yield StatoPendenza.SCADUTO;
                }
                yield StatoPendenza.NON_ESEGUITO;
            }
            case ESEGUITO -> StatoPendenza.ESEGUITO;
            case PARZIALMENTE_ESEGUITO -> StatoPendenza.PARZIALMENTE_ESEGUITO;
            case ANNULLATO -> StatoPendenza.ANNULLATO;
            case ESEGUITO_ALTRO_CANALE -> StatoPendenza.ESEGUITO_ALTRO_CANALE;
            case ANOMALO -> StatoPendenza.ANOMALO;
            case ESEGUITO_SENZA_RPT -> StatoPendenza.ESEGUITO_SENZA_RPT;
            case INCASSATO -> StatoPendenza.INCASSATO;
        };
    }

    static DominioRef mapDominio(Dominio dominio) {
        if (dominio == null) {
            return null;
        }
        DominioRef ref = new DominioRef(dominio.getCodDominio());
        ref.setRagioneSociale(dominio.getRagioneSociale());
        return ref;
    }

    static TipoPendenzaRef mapTipoPendenza(TipoVersamento t) {
        if (t == null) {
            return null;
        }
        TipoPendenzaRef ref = new TipoPendenzaRef(t.getCodTipoVersamento());
        ref.setDescrizione(t.getDescrizione());
        return ref;
    }

    /**
     * Riferimento alla unita' operativa. La unita' {@code EC} rappresenta l'ente creditore
     * stesso e non una sotto-struttura scelta dal chiamante: non si espone.
     */
    static UnitaOperativaRef mapUnitaOperativa(UnitaOperativa uo) {
        if (uo == null || COD_UNITA_OPERATIVA_EC.equals(uo.getCodUo())) {
            return null;
        }
        return new UnitaOperativaRef(uo.getCodUo(), uo.getUoDenominazione());
    }

    /** {@code dataValidita} e' una data pura nel contratto: tronchiamo l'orario. */
    static LocalDate toLocalDate(OffsetDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    /**
     * Dettaglio di una pendenza del modello v3. La pendenza deve avere un'opzione di
     * pagamento con la sua posizione debitoria caricate.
     *
     * @param idA2A           identificativo del gestionale, come richiesto dal chiamante
     * @param contesto        anagrafiche di dominio, tipo pendenza e unita' operativa
     * @param anagraficheVoci anagrafiche delle voci
     */
    public Pendenza toDetail(String idA2A, it.govpay.pendenze.entity.Pendenza p,
                             PendenzaAnagrafiche.Contesto contesto,
                             PendenzaAnagrafiche.Voci anagraficheVoci,
                             Set<PendenzaExpand> expand) {
        if (p == null) {
            return null;
        }
        OpzionePagamento opzione = p.getOpzionePagamento();
        PosizioneDebitoria posizione = opzione.getPosizioneDebitoria();
        Dominio dominio = contesto.dominio();

        Pendenza dettaglio = new Pendenza()
                .idA2A(idA2A)
                .idPendenza(p.getIdPendenza())
                .stato(mapStato(p.getStato(), p.getDataScadenzaAvviso(), posizione))
                .dominio(mapDominio(dominio))
                .tipoPendenza(mapTipoPendenza(contesto.tipoVersamento()))
                .unitaOperativa(mapUnitaOperativa(contesto.unitaOperativa()))
                .importo(p.getImporto())
                .causale(CausaleVersamentoDecoder.decodeSimple(p.getCausaleVersamento()))
                .numeroAvviso(p.getNumeroAvviso())
                .iuvAvviso(p.getIuv())
                .dataScadenza(p.getDataScadenzaAvviso())
                .dataValidita(toLocalDate(p.getDataValidita()))
                .dataCreazione(p.getDataCreazione())
                .dataUltimoAggiornamento(p.getDataUltimoAggiornamento())
                .dataUltimaModificaAca(p.getDataUltimaModificaAca())
                .dataUltimaComunicazioneAca(p.getDataUltimaComunicazioneAca())
                .direzione(p.getDirezione())
                .divisione(p.getDivisione())
                .debitore(mapDebitore(opzione))
                .opzionePagamento(mapOpzione(opzione, p.getNumeroRata()))
                .posizioneDebitoria(mapPosizione(idA2A, dominio, posizione))
                .voci(mapVoci(p, anagraficheVoci));

        Set<PendenzaExpand> requested = expand != null ? expand : Set.of();
        if (requested.contains(PendenzaExpand.DATI_ALLEGATI)) {
            dettaglio.datiAllegati(p.getDatiAllegati());
        }
        if (requested.contains(PendenzaExpand.PROPRIETA)) {
            dettaglio.proprieta(p.getProprieta());
        }
        return dettaglio;
    }

    private static RiferimentoDebitore mapDebitore(OpzionePagamento opzione) {
        Debitore debitore = opzione.getPdDebitore().getDebitore();
        return new RiferimentoDebitore(
                RiferimentoDebitore.TipoEnum.valueOf(debitore.getTipo().name()),
                debitore.getIdentificativo());
    }

    private static OpzionePagamentoRef mapOpzione(OpzionePagamento opzione, int numeroRata) {
        boolean pianoRateale = opzione.getTipologia() == TipologiaOpzionePagamento.PIANO_RATEALE;
        return new OpzionePagamentoRef(
                opzione.getIdOpzionePagamento(),
                OpzionePagamentoRef.TipologiaEnum.valueOf(opzione.getTipologia().name()),
                OpzionePagamentoRef.StatoEnum.valueOf(opzione.getStato().name()))
                .numeroRata(pianoRateale ? numeroRata : null);
    }

    private static PosizioneDebitoriaRef mapPosizione(String idA2A, Dominio dominio, PosizioneDebitoria posizione) {
        return new PosizioneDebitoriaRef(IdentificatorePosizioneDebitoria.comporre(
                idA2A, dominio.getCodDominio(), posizione.getCodDocumento()))
                .dataPubblicazione(posizione.getDataPubblicazione());
    }

    static List<VocePendenza> mapVoci(it.govpay.pendenze.entity.Pendenza p, PendenzaAnagrafiche.Voci anagrafiche) {
        return p.getVoci().stream()
                .sorted(Comparator.comparingInt(it.govpay.pendenze.entity.VocePendenza::getIndice))
                .map(voce -> toVocePendenza(voce, p.getIdDominio(), anagrafiche))
                .toList();
    }

    static VocePendenza toVocePendenza(it.govpay.pendenze.entity.VocePendenza voce, Long idDominioPendenza,
                                       PendenzaAnagrafiche.Voci anagrafiche) {
        VocePendenza dto = new VocePendenza()
                .idVocePendenza(voce.getIdVocePendenza())
                .indice(voce.getIndice())
                .importo(voce.getImporto())
                .descrizione(voce.getDescrizione())
                .stato(mapStatoVoce(voce.getStato()))
                .descrizioneCausaleRPT(voce.getDescrizioneCausaleRpt())
                .contabilita(new DettaglioContabileConverter().convertToDatabaseColumn(voce.getDettaglioContabile()))
                .datiAllegati(voce.getDatiAllegati())
                .metadata(voce.getMetadata())
                .dettaglio(mapDettaglioVoce(voce, anagrafiche));

        DominioRef dominioVoce = mapDominioVoce(voce.getIdDominio(), idDominioPendenza, anagrafiche);
        if (dominioVoce != null) {
            dto.dominio(dominioVoce);
        }
        return dto;
    }

    /**
     * Dominio della voce solo per pendenze multibeneficiario: valorizzato quando
     * la voce ha un dominio diverso da quello della pendenza. Single-beneficiario
     * (dominio voce assente o uguale al padre) → null.
     */
    static DominioRef mapDominioVoce(Long idDominioVoce, Long idDominioPendenza,
                                     PendenzaAnagrafiche.Voci anagrafiche) {
        if (idDominioVoce == null || idDominioVoce.equals(idDominioPendenza)) {
            return null;
        }
        return mapDominio(anagrafiche.domini().get(idDominioVoce));
    }

    static final String TIPO_VOCE_ENTRATA_ANAGRAFICA = "ENTRATA_ANAGRAFICA";
    static final String TIPO_VOCE_INCASSO_DIRETTO = "INCASSO_DIRETTO";
    static final String TIPO_VOCE_BOLLO_TELEMATICO = "BOLLO_TELEMATICO";

    /**
     * Tipo effettivo della voce, dal tipo di riferimento che la libreria
     * memorizza: entrata in anagrafica, incasso diretto con IBAN, bollo telematico.
     */
    static VocePendenzaDettaglio mapDettaglioVoce(it.govpay.pendenze.entity.VocePendenza voce,
                                                  PendenzaAnagrafiche.Voci anagrafiche) {
        if (voce.getTipoRiferimento() == null) {
            return null;
        }
        return switch (voce.getTipoRiferimento()) {
            case BOLLO -> new VocePendenzaBolloTelematico(
                    TIPO_VOCE_BOLLO_TELEMATICO,
                    VocePendenzaBolloTelematico.TipoBolloEnum.IMPOSTA_DI_BOLLO,
                    voce.getHashDocumento(),
                    voce.getProvinciaResidenza());
            case RIFERIMENTO_ENTRATA -> new VocePendenzaEntrataAnagrafica(
                    TIPO_VOCE_ENTRATA_ANAGRAFICA,
                    anagrafiche.codiciEntrata().get(voce.getIdTributo()));
            case ENTRATA -> {
                VocePendenzaIncassoDiretto incasso = new VocePendenzaIncassoDiretto(
                        TIPO_VOCE_INCASSO_DIRETTO,
                        anagrafiche.codiciIban().get(voce.getIdIbanAccredito()),
                        voce.getTipoContabilita() == null ? null
                                : TipoContabilita.valueOf(voce.getTipoContabilita().name()),
                        voce.getCodiceContabilita());
                if (voce.getIdIbanAppoggio() != null) {
                    incasso.ibanAppoggio(anagrafiche.codiciIban().get(voce.getIdIbanAppoggio()));
                }
                yield incasso;
            }
        };
    }

    static StatoVocePendenza mapStatoVoce(it.govpay.pendenze.model.StatoVocePendenza stato) {
        if (stato == null) {
            return null;
        }
        return switch (stato) {
            case NON_ESEGUITO -> StatoVocePendenza.NON_ESEGUITO;
            case ESEGUITO -> StatoVocePendenza.ESEGUITO;
            case ANOMALO -> StatoVocePendenza.ANOMALO;
        };
    }
}
