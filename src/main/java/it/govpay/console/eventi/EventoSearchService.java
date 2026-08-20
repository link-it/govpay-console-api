package it.govpay.console.eventi;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.model.EventoSummary;
import it.govpay.console.model.ListEventi200Response;
import it.govpay.console.model.Pagination;
import it.govpay.console.pagination.CursorCodec;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.gde.client.beans.Evento;
import it.govpay.gde.client.beans.ListaEventi;

/**
 * Ricerca paginata della collection {@code GET /eventi}. Delega il fetch dei
 * dati a {@link EventoGdeClient} (servizio GDE, esterno): qui vivono solo ACL,
 * default temporali, validazioni di costo e traduzione cursor
 * pubblico/opaco ↔ keyset esplicito di GDE. Metadata-only: nessun payload,
 * quindi nessun audit GDPR sulla lista.
 */
@Service
public class EventoSearchService {

    private static final Logger log = LoggerFactory.getLogger(EventoSearchService.class);

    private final EventoGdeClient client;
    private final EventoMapper mapper;
    private final EventoAcl eventoAcl;
    private final CurrentOperatorService currentOperatorService;

    public EventoSearchService(EventoGdeClient client, EventoMapper mapper,
            EventoAcl eventoAcl, CurrentOperatorService currentOperatorService) {
        this.client = client;
        this.mapper = mapper;
        this.eventoAcl = eventoAcl;
        this.currentOperatorService = currentOperatorService;
    }

    @Transactional(readOnly = true)
    public ListEventi200Response search(EventoListQuery query) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("listEventi filtri[idDominio={}, iuv={}, componente={}, esito={}], page={}, limit={}, "
                        + "total={}, cursor={}, operatore={}",
                query.idDominio(), query.iuv(), query.componente(), query.esito(),
                query.page(), query.limit(), query.total(), query.cursor() != null, operatore.principal());

        ResolvedDomini domini = resolveIdDominio(query.idDominio(), operatore);

        ListEventi200Response response = new ListEventi200Response();
        if (domini.nessunDominioVisibile()) {
            response.setResults(List.of());
            if (query.cursor() == null) {
                response.setPagination(new Pagination(query.page(), query.limit(), false));
            }
            return response;
        }

        OffsetDateTime dataDa = query.dataDa();
        OffsetDateTime dataA = query.dataA();
        if (dataDa == null && dataA == null) {
            dataDa = OffsetDateTime.now().minusHours(24);
        }

        if (query.messaggi() != null && !query.messaggi().isBlank()) {
            requireRangeEntroOre(dataDa, dataA, 24 * 7,
                    "Il filtro 'messaggi' richiede un intervallo temporale (dataDa/dataA) di al massimo 7 giorni.");
        }

        return query.cursor() != null
                ? searchCursorMode(query, domini.codici(), dataDa, dataA, response)
                : searchOffsetMode(query, domini.codici(), dataDa, dataA, response);
    }

    private ListEventi200Response searchOffsetMode(EventoListQuery query, List<String> domini,
            OffsetDateTime dataDa, OffsetDateTime dataA, ListEventi200Response response) {
        boolean wantTotal = Boolean.TRUE.equals(query.total());
        if (wantTotal) {
            requireRangeEntroOre(dataDa, dataA, 24,
                    "Il conteggio (?total=true) richiede un intervallo temporale (dataDa/dataA) di al massimo 24 ore.");
        }

        long offset = (long) (query.page() - 1) * query.limit();

        EventoGdeQuery gdeQuery = new EventoGdeQuery(
                query.limit(), offset, false, null, null, wantTotal,
                dataDa, dataA, domini,
                query.iuv(), query.ccp(), query.idA2A(), query.idPendenza(),
                name(query.componente()), name(query.categoria()), name(query.esito()), name(query.ruolo()),
                query.tipoEvento(), query.sottotipoEvento(),
                query.severitaDa(), query.severitaA(), query.messaggi());

        ListaEventi gdeResult = client.findEventi(gdeQuery);
        response.setResults(toSummaries(gdeResult));

        Pagination pagination = new Pagination(query.page(), query.limit(), hasNext(gdeResult));
        if (wantTotal) {
            Long total = gdeResult.getPage() != null ? gdeResult.getPage().getTotal() : null;
            pagination.setTotalResults(total);
            if (total != null) {
                pagination.setTotalPages((int) Math.ceil(total / (double) query.limit()));
            }
        }
        response.setPagination(pagination);
        return response;
    }

    private ListEventi200Response searchCursorMode(EventoListQuery query, List<String> domini,
            OffsetDateTime dataDa, OffsetDateTime dataA, ListEventi200Response response) {
        CursorCodec.Cursor cursor = query.cursor().isBlank() ? null : CursorCodec.decode(query.cursor());

        EventoGdeQuery gdeQuery = new EventoGdeQuery(
                query.limit(), 0, true,
                cursor != null ? cursor.timestamp() : null,
                cursor != null ? cursor.id() : null,
                false,
                dataDa, dataA, domini,
                query.iuv(), query.ccp(), query.idA2A(), query.idPendenza(),
                name(query.componente()), name(query.categoria()), name(query.esito()), name(query.ruolo()),
                query.tipoEvento(), query.sottotipoEvento(),
                query.severitaDa(), query.severitaA(), query.messaggi());

        ListaEventi gdeResult = client.findEventi(gdeQuery);
        List<EventoSummary> summaries = toSummaries(gdeResult);
        response.setResults(summaries);

        if (hasNext(gdeResult) && !summaries.isEmpty()) {
            EventoSummary last = summaries.get(summaries.size() - 1);
            response.setNextCursor(CursorCodec.encode(last.getDataEvento(), last.getId()));
        }
        return response;
    }

    private List<EventoSummary> toSummaries(ListaEventi result) {
        List<Evento> items = result.getItems();
        return items == null ? List.of() : items.stream().map(mapper::toSummary).toList();
    }

    private static boolean hasNext(ListaEventi result) {
        return result.getPage() != null && Boolean.TRUE.equals(result.getPage().isHasNext());
    }

    /**
     * Risolve il filtro {@code idDominio} da inviare a GDE combinando il
     * filtro esplicito del client (se presente) con l'ACL dell'operatore:
     * - {@code tuttiIDomini}: nessun vincolo (lista vuota = "tutti", GDE
     *   omette il parametro e ritorna anche gli eventi senza dominio).
     * - altrimenti: solo i domini "interi" dell'operatore (stesso criterio di
     *   {@code DominioVisibilita}, nessuna estensione via UO — gli eventi
     *   correlano solo a idDominio, non a una UO specifica).
     * Un filtro esplicito fuori dai domini visibili, o un operatore senza
     * alcun dominio visibile, produce {@code nessunDominioVisibile=true}: il
     * chiamante deve rispondere con lista vuota senza interrogare GDE (mai
     * 403, mai leak).
     */
    private ResolvedDomini resolveIdDominio(String richiesto, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            return richiesto != null
                    ? new ResolvedDomini(false, List.of(richiesto))
                    : new ResolvedDomini(false, List.of());
        }
        List<String> visibili = eventoAcl.codiciVisibili(operatore);
        if (visibili.isEmpty()) {
            return new ResolvedDomini(true, List.of());
        }
        if (richiesto != null) {
            return visibili.contains(richiesto)
                    ? new ResolvedDomini(false, List.of(richiesto))
                    : new ResolvedDomini(true, List.of());
        }
        return new ResolvedDomini(false, visibili);
    }

    private static void requireRangeEntroOre(OffsetDateTime dataDa, OffsetDateTime dataA, long oreMax, String message) {
        if (dataDa == null) {
            throw new BadRequestException(message);
        }
        OffsetDateTime a = dataA != null ? dataA : OffsetDateTime.now();
        Duration d = Duration.between(dataDa, a);
        if (d.isNegative() || d.toHours() > oreMax) {
            throw new BadRequestException(message);
        }
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private record ResolvedDomini(boolean nessunDominioVisibile, List<String> codici) {
    }
}
