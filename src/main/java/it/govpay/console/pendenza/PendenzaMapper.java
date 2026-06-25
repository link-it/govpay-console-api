package it.govpay.console.pendenza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import it.govpay.console.common.CausaleVersamentoDecoder;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.DominioRef;
import it.govpay.console.model.Pendenza;
import it.govpay.console.model.PendenzaExpand;
import it.govpay.console.model.PendenzaSummary;
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

@Component
public class PendenzaMapper {

    private static final Logger log = LoggerFactory.getLogger(PendenzaMapper.class);

    private final Clock clock;

    public PendenzaMapper(Clock clock) {
        this.clock = clock;
    }

    public PendenzaSummary toSummary(Versamento v) {
        if (v == null) {
            return null;
        }
        Applicazione applicazione = v.getApplicazione();
        Dominio dominio = v.getDominio();
        return new PendenzaSummary(
                applicazione != null ? applicazione.getCodApplicazione() : null,
                v.getCodVersamentoEnte(),
                mapStato(v.getStatoVersamento(), v.getDataScadenza()),
                mapDominio(dominio),
                mapTipoPendenza(resolveTipoPendenza(v)),
                v.getImportoTotale(),
                CausaleVersamentoDecoder.decodeSimple(v.getCausaleVersamento()),
                v.getDataOraUltimoAggiornamento())
                .unitaOperativa(mapUnitaOperativa(v.getUnitaOperativa()))
                .numeroAvviso(v.getNumeroAvviso())
                .iuvAvviso(v.getIuvVersamento())
                .dataScadenza(v.getDataScadenza())
                .dataValidita(toLocalDate(v.getDataValidita()))
                .dataUltimaModificaAca(v.getDataUltimaModificaAca())
                .dataUltimaComunicazioneAca(v.getDataUltimaComunicazioneAca());
    }

    /**
     * Mappa lo stato V1 nel corrispondente {@link StatoPendenza} V2, applicando
     * la derivazione SCADUTA come fa V1 (vedi `PendenzeDAO`):
     * <ul>
     *   <li>{@code NON_ESEGUITO} + {@code dataScadenza < now} → {@link StatoPendenza#SCADUTA};</li>
     *   <li>{@code NON_ESEGUITO} + {@code dataScadenza >= now} (o null) → {@link StatoPendenza#NON_PAGATA};</li>
     *   <li>altri stati: mapping diretto.</li>
     * </ul>
     */
    StatoPendenza mapStato(String statoV1, OffsetDateTime dataScadenza) {
        if (statoV1 == null) {
            return null;
        }
        StatoPendenza base = baseMapStato(statoV1);
        if (base == StatoPendenza.NON_PAGATA
                && dataScadenza != null
                && dataScadenza.isBefore(OffsetDateTime.now(clock))) {
            return StatoPendenza.SCADUTA;
        }
        return base;
    }

    private static StatoPendenza baseMapStato(String statoV1) {
        // V1 usa varianti femminili/maschili; normalizziamo prima di mappare.
        String normalized = statoV1.trim().toUpperCase();
        return switch (normalized) {
            case "ESEGUITA", "ESEGUITO", "PAGATA", "PAGATO" -> StatoPendenza.PAGATA;
            case "NON_ESEGUITA", "NON_ESEGUITO", "NON_PAGATA", "NON_PAGATO" -> StatoPendenza.NON_PAGATA;
            case "ESEGUITA_PARZIALE", "ESEGUITO_PARZIALE", "PAGATA_PARZIALE", "PAGATO_PARZIALE" ->
                    StatoPendenza.PAGATA_PARZIALE;
            case "INCASSATA", "INCASSATO", "RICONCILIATA", "RICONCILIATO" -> StatoPendenza.RICONCILIATA;
            case "ANNULLATA", "ANNULLATO" -> StatoPendenza.ANNULLATA;
            case "SCADUTA", "SCADUTO" -> StatoPendenza.SCADUTA;
            case "ANOMALA", "ANOMALO" -> StatoPendenza.ANOMALA;
            default -> {
                log.warn("Stato versamento V1 sconosciuto, mappato a ANOMALA: {}", statoV1);
                yield StatoPendenza.ANOMALA;
            }
        };
    }

    static DominioRef mapDominio(Dominio dominio) {
        if (dominio == null) {
            return null;
        }
        return new DominioRef(dominio.getCodDominio(), dominio.getRagioneSociale());
    }

    static TipoPendenzaRef mapTipoPendenza(TipoVersamento t) {
        if (t == null) {
            return null;
        }
        return new TipoPendenzaRef(t.getCodTipoVersamento(), t.getDescrizione());
    }

    /**
     * V1 ricava il TipoPendenzaRef passando per {@code TipoVersamentoDominio}
     * (configurazione per-dominio): ricalchiamo la stessa catena con fallback
     * sul {@link TipoVersamento} globale se la TVD non e' caricata.
     */
    private static TipoVersamento resolveTipoPendenza(Versamento v) {
        if (v.getTipoVersamentoDominio() != null
                && v.getTipoVersamentoDominio().getTipoVersamento() != null) {
            return v.getTipoVersamentoDominio().getTipoVersamento();
        }
        return v.getTipoVersamento();
    }

    static UnitaOperativaRef mapUnitaOperativa(UnitaOperativa uo) {
        if (uo == null) {
            return null;
        }
        return new UnitaOperativaRef(uo.getCodUo(), uo.getUoDenominazione());
    }

    /** {@code dataValidita} e' una data pura nel contratto: tronchiamo l'orario. */
    static LocalDate toLocalDate(OffsetDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    public Pendenza toDetail(Versamento v, Set<PendenzaExpand> expand) {
        if (v == null) {
            return null;
        }
        Applicazione applicazione = v.getApplicazione();
        Pendenza p = new Pendenza()
                .idA2A(applicazione != null ? applicazione.getCodApplicazione() : null)
                .idPendenza(v.getCodVersamentoEnte())
                .stato(mapStato(v.getStatoVersamento(), v.getDataScadenza()))
                .dominio(mapDominio(v.getDominio()))
                .tipoPendenza(mapTipoPendenza(resolveTipoPendenza(v)))
                .unitaOperativa(mapUnitaOperativa(v.getUnitaOperativa()))
                .importo(v.getImportoTotale())
                .causale(CausaleVersamentoDecoder.decodeSimple(v.getCausaleVersamento()))
                .numeroAvviso(v.getNumeroAvviso())
                .iuvAvviso(v.getIuvVersamento())
                .dataScadenza(v.getDataScadenza())
                .dataValidita(toLocalDate(v.getDataValidita()))
                .dataUltimoAggiornamento(v.getDataOraUltimoAggiornamento())
                .dataUltimaModificaAca(v.getDataUltimaModificaAca())
                .dataUltimaComunicazioneAca(v.getDataUltimaComunicazioneAca())
                .direzione(v.getDirezione())
                .divisione(v.getDivisione())
                .voci(mapVoci(v.getSingoliVersamenti(), v.getDominio()));

        Set<PendenzaExpand> requested = expand != null ? expand : Set.of();
        if (requested.contains(PendenzaExpand.DATI_ALLEGATI)) {
            p.datiAllegati(v.getDatiAllegati());
        }
        if (requested.contains(PendenzaExpand.PROPRIETA)) {
            p.proprieta(v.getProprieta());
        }
        return p;
    }

    static List<VocePendenza> mapVoci(List<SingoloVersamento> singoliVersamenti, Dominio dominioPendenza) {
        if (singoliVersamenti == null) {
            return List.of();
        }
        return singoliVersamenti.stream()
                .sorted((a, b) -> Integer.compare(
                        a.getIndiceDati() != null ? a.getIndiceDati() : 0,
                        b.getIndiceDati() != null ? b.getIndiceDati() : 0))
                .map(sv -> toVocePendenza(sv, dominioPendenza))
                .toList();
    }

    static VocePendenza toVocePendenza(SingoloVersamento sv, Dominio dominioPendenza) {
        VocePendenza voce = new VocePendenza()
                .idVocePendenza(sv.getCodSingoloVersamentoEnte())
                .indice(sv.getIndiceDati())
                .importo(sv.getImportoSingoloVersamento())
                .descrizione(sv.getDescrizione())
                .stato(mapStatoVoce(sv.getStatoSingoloVersamento()))
                .descrizioneCausaleRPT(sv.getDescrizioneCausaleRpt())
                .contabilita(sv.getContabilita())
                .datiAllegati(sv.getDatiAllegati())
                .metadata(sv.getMetadata())
                .dettaglio(mapDettaglioVoce(sv));

        DominioRef dominioVoce = mapDominioVoce(sv.getDominio(), dominioPendenza);
        if (dominioVoce != null) {
            voce.dominio(dominioVoce);
        }
        return voce;
    }

    /**
     * Dominio della voce solo per pendenze multibeneficiario: valorizzato quando
     * la voce ha un dominio diverso da quello della pendenza. Single-beneficiario
     * (dominio voce assente o uguale al padre) → null.
     */
    static DominioRef mapDominioVoce(Dominio dominioVoce, Dominio dominioPendenza) {
        if (dominioVoce == null) {
            return null;
        }
        if (dominioPendenza != null && dominioVoce.getId() != null
                && dominioVoce.getId().equals(dominioPendenza.getId())) {
            return null;
        }
        return mapDominio(dominioVoce);
    }

    static final String TIPO_VOCE_ENTRATA_ANAGRAFICA = "ENTRATA_ANAGRAFICA";
    static final String TIPO_VOCE_INCASSO_DIRETTO = "INCASSO_DIRETTO";
    static final String TIPO_VOCE_BOLLO_TELEMATICO = "BOLLO_TELEMATICO";

    /**
     * Discrimina il tipo effettivo della voce. Priorita' (per voci legacy con dati
     * ambigui): {@code BOLLO_TELEMATICO} → {@code ENTRATA_ANAGRAFICA} →
     * {@code INCASSO_DIRETTO}. Se piu' tipi sono compresenti, logga un WARN e
     * applica la priorita'. Se nessuna variante e' riconoscibile, ritorna null.
     */
    static VocePendenzaDettaglio mapDettaglioVoce(SingoloVersamento sv) {
        boolean isBollo = sv.getTipoBollo() != null && sv.getHashDocumento() != null
                && sv.getProvinciaResidenza() != null;
        boolean isEntrata = sv.getTributo() != null && sv.getTributo().getTipoTributo() != null;
        boolean isIncasso = sv.getIbanAccredito() != null;

        int segnali = (isBollo ? 1 : 0) + (isEntrata ? 1 : 0) + (isIncasso ? 1 : 0);
        if (segnali > 1) {
            log.warn("Voce {} ambigua: piu' tipi compresenti (bollo={}, entrata={}, incasso={}); "
                            + "applico la priorita' BOLLO_TELEMATICO > ENTRATA_ANAGRAFICA > INCASSO_DIRETTO",
                    sv.getCodSingoloVersamentoEnte(), isBollo, isEntrata, isIncasso);
        }

        if (isBollo) {
            return new VocePendenzaBolloTelematico(
                    TIPO_VOCE_BOLLO_TELEMATICO,
                    mapTipoBollo(sv.getTipoBollo()),
                    sv.getHashDocumento(),
                    sv.getProvinciaResidenza());
        }
        if (isEntrata) {
            return new VocePendenzaEntrataAnagrafica(
                    TIPO_VOCE_ENTRATA_ANAGRAFICA,
                    sv.getTributo().getTipoTributo().getCodTributo());
        }
        if (isIncasso) {
            VocePendenzaIncassoDiretto incasso = new VocePendenzaIncassoDiretto(
                    TIPO_VOCE_INCASSO_DIRETTO,
                    sv.getIbanAccredito().getCodIban(),
                    mapTipoContabilita(sv.getTipoContabilita()),
                    sv.getCodiceContabilita());
            if (sv.getIbanAppoggio() != null) {
                incasso.ibanAppoggio(sv.getIbanAppoggio().getCodIban());
            }
            return incasso;
        }
        return null;
    }

    private static VocePendenzaBolloTelematico.TipoBolloEnum mapTipoBollo(String tipoBolloV1) {
        // V1 codifica il bollo telematico con "01"; il modello V2 espone l'unica
        // tipologia supportata "Imposta di bollo".
        return VocePendenzaBolloTelematico.TipoBolloEnum.IMPOSTA_DI_BOLLO;
    }

    /**
     * Mappa la codifica DB a 1 char ({@code tipo_contabilita}) sull'enum V2.
     * Codifica allineata a V1: 0=CAPITOLO, 1=SPECIALE, 2=SIOPE, 6/7/8=SRTP*, 9=ALTRO.
     */
    static TipoContabilita mapTipoContabilita(String codice) {
        if (codice == null) {
            return null;
        }
        return switch (codice.trim()) {
            case "0" -> TipoContabilita.CAPITOLO;
            case "1" -> TipoContabilita.SPECIALE;
            case "2" -> TipoContabilita.SIOPE;
            case "6" -> TipoContabilita.SRTP_ESCLUSA_RAVV_OPEROSO;
            case "7" -> TipoContabilita.SRTP_ESCLUSA_ALTRO_OPERATORE;
            case "8" -> TipoContabilita.SRTP_ESCLUSA;
            case "9" -> TipoContabilita.ALTRO;
            default -> {
                log.warn("Codifica tipo_contabilita V1 sconosciuta, mappata a ALTRO: {}", codice);
                yield TipoContabilita.ALTRO;
            }
        };
    }

    static StatoVocePendenza mapStatoVoce(String statoV1) {
        if (statoV1 == null) {
            return null;
        }
        String normalized = statoV1.trim().toUpperCase();
        return switch (normalized) {
            case "ESEGUITO", "ESEGUITA", "PAGATO", "PAGATA" -> StatoVocePendenza.PAGATA;
            case "NON_ESEGUITO", "NON_ESEGUITA", "NON_PAGATO", "NON_PAGATA" -> StatoVocePendenza.NON_PAGATA;
            case "ANOMALA", "ANOMALO" -> StatoVocePendenza.ANOMALA;
            default -> {
                log.warn("Stato voce V1 sconosciuto, mappato a ANOMALA: {}", statoV1);
                yield StatoVocePendenza.ANOMALA;
            }
        };
    }
}
