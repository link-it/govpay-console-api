package it.govpay.console.pendenza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

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
import it.govpay.console.model.TipoPendenza;
import it.govpay.console.model.TipoPendenzaRef;
import it.govpay.console.model.UnitaOperativaRef;
import it.govpay.console.model.VocePendenza;

@Component
public class PendenzaMapper {

    private static final Logger log = LoggerFactory.getLogger(PendenzaMapper.class);

    private static final int CAUSALE_BREVE_MAX_LEN = 80;

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
                mapTipo(v.getTipo()),
                Boolean.TRUE.equals(v.getAnomalo()),
                Boolean.TRUE.equals(v.getAck()),
                mapDominio(dominio),
                mapTipoPendenza(resolveTipoPendenza(v)),
                v.getImportoTotale(),
                v.getImportoPagato(),
                v.getDataOraUltimoAggiornamento(),
                v.getSrcDebitoreIdentificativo())
                .unitaOperativa(mapUnitaOperativa(v.getUnitaOperativa()))
                .dataScadenza(v.getDataScadenza())
                .numeroAvviso(v.getNumeroAvviso())
                .iuvAvviso(v.getIuvVersamento())
                .causaleBreve(truncate(v.getCausaleVersamento()));
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

    static TipoPendenza mapTipo(String tipoV1) {
        if (tipoV1 == null) {
            return null;
        }
        String normalized = tipoV1.trim().toUpperCase();
        return switch (normalized) {
            case "DOVUTA", "DOVUTO" -> TipoPendenza.DOVUTA;
            case "SPONTANEA", "SPONTANEO" -> TipoPendenza.SPONTANEA;
            default -> {
                log.warn("Tipo versamento V1 sconosciuto: {}", tipoV1);
                yield null;
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

    static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= CAUSALE_BREVE_MAX_LEN
                ? value
                : value.substring(0, CAUSALE_BREVE_MAX_LEN);
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
                .tipo(mapTipo(v.getTipo()))
                .anomalo(Boolean.TRUE.equals(v.getAnomalo()))
                .verificato(Boolean.TRUE.equals(v.getAck()))
                .dominio(mapDominio(v.getDominio()))
                .tipoPendenza(mapTipoPendenza(resolveTipoPendenza(v)))
                .unitaOperativa(mapUnitaOperativa(v.getUnitaOperativa()))
                .importo(v.getImportoTotale())
                .importoPagato(v.getImportoPagato())
                .dataScadenza(v.getDataScadenza())
                .dataUltimoAggiornamento(v.getDataOraUltimoAggiornamento())
                .numeroAvviso(v.getNumeroAvviso())
                .iuvAvviso(v.getIuvVersamento())
                .causaleBreve(truncate(v.getCausaleVersamento()))
                .idDebitore(v.getSrcDebitoreIdentificativo())
                .tassonomia(v.getTassonomia())
                .tassonomiaAvviso(v.getTassonomiaAvviso())
                .direzione(v.getDirezione())
                .divisione(v.getDivisione())
                .voci(mapVoci(v.getSingoliVersamenti()));

        Set<PendenzaExpand> requested = expand != null ? expand : Set.of();
        if (requested.contains(PendenzaExpand.DATI_ALLEGATI)) {
            p.datiAllegati(v.getDatiAllegati());
        }
        if (requested.contains(PendenzaExpand.PROPRIETA)) {
            p.proprieta(v.getProprieta());
        }
        return p;
    }

    static List<VocePendenza> mapVoci(List<SingoloVersamento> singoliVersamenti) {
        if (singoliVersamenti == null) {
            return List.of();
        }
        return singoliVersamenti.stream()
                .sorted((a, b) -> Integer.compare(
                        a.getIndiceDati() != null ? a.getIndiceDati() : 0,
                        b.getIndiceDati() != null ? b.getIndiceDati() : 0))
                .map(PendenzaMapper::toVocePendenza)
                .toList();
    }

    static VocePendenza toVocePendenza(SingoloVersamento sv) {
        return new VocePendenza()
                .idVocePendenza(sv.getCodSingoloVersamentoEnte())
                .indice(sv.getIndiceDati())
                .importo(sv.getImportoSingoloVersamento())
                .descrizione(sv.getDescrizione())
                .stato(mapStatoVoce(sv.getStatoSingoloVersamento()))
                .descrizioneCausaleRPT(sv.getDescrizioneCausaleRpt())
                .contabilita(sv.getContabilita())
                .datiAllegati(sv.getDatiAllegati())
                .metadata(sv.getMetadata());
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
