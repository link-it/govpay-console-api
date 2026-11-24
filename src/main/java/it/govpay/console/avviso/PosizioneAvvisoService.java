package it.govpay.console.avviso;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.metrics.ExternalCallMetricsRecorder;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.pendenza.PendenzaAnagrafiche;
import it.govpay.console.pendenza.PosizioneGrafo;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.NotAcceptableMediaTypeException;
import it.govpay.console.web.NotFoundException;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.PdDebitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.stampe.client.model.PaymentNotice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servizio dell'endpoint {@code GET /posizioni-debitorie/{idA2A}/{idPosizioneDebitoria}/avviso}:
 * il PDF dell'avviso di un debitore della posizione, con tutte le sue opzioni di pagamento.
 *
 * <p>Si stampano le pendenze {@code NON_ESEGUITO} di ogni opzione, in ordine di rata. L'operatore
 * deve vedere tutte le pendenze da stampare: un avviso con rate mancanti non e' ammesso.
 * Il PDF e' scritto in streaming direttamente sulla risposta.
 */
@Service
public class PosizioneAvvisoService {

    private static final Logger log = LoggerFactory.getLogger(PosizioneAvvisoService.class);

    private final PosizioneGrafo grafo;
    private final AvvisoPdfPayloadMapper pdfPayloadMapper;
    private final IbanPostaleResolver ibanPostaleResolver;
    private final StampeClient stampeClient;
    private final CurrentOperatorService currentOperatorService;
    private final ExternalCallMetricsRecorder externalCallMetricsRecorder;

    public PosizioneAvvisoService(PosizioneGrafo grafo,
                                  AvvisoPdfPayloadMapper pdfPayloadMapper,
                                  IbanPostaleResolver ibanPostaleResolver,
                                  StampeClient stampeClient,
                                  CurrentOperatorService currentOperatorService,
                                  ExternalCallMetricsRecorder externalCallMetricsRecorder) {
        this.grafo = grafo;
        this.pdfPayloadMapper = pdfPayloadMapper;
        this.ibanPostaleResolver = ibanPostaleResolver;
        this.stampeClient = stampeClient;
        this.currentOperatorService = currentOperatorService;
        this.externalCallMetricsRecorder = externalCallMetricsRecorder;
    }

    @Transactional(readOnly = true)
    public void stampa(String idA2A, String idPosizioneDebitoria, String idDebitore,
                       HttpServletRequest request, HttpServletResponse response) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("getAvvisoPosizioneDebitoria idA2A={} idPosizioneDebitoria={} operatore={}",
                idA2A, idPosizioneDebitoria, operatore.principal());
        verificaAcceptCompatibileConPdf(request);

        PosizioneGrafo.Caricato caricato = grafo.carica(idA2A, idPosizioneDebitoria);
        Map<Long, Boolean> visibili = grafo.visibilita(caricato, operatore);
        if (!visibili.containsValue(true)) {
            throw grafo.nonTrovata(idA2A, idPosizioneDebitoria);
        }

        PdDebitore debitore = debitoreDaStampare(caricato, idDebitore);
        List<List<Pendenza>> opzioni = opzioniStampabili(caricato, debitore);
        if (opzioni.isEmpty()) {
            throw new AvvisoNonDisponibileException("Non sono state trovate opzioni di pagamento con rate "
                    + "pagabili da includere nella stampa della posizione debitoria.");
        }
        List<Pendenza> daStampare = opzioni.stream().flatMap(List::stream).toList();
        if (daStampare.stream().anyMatch(p -> !visibili.get(p.getId()))) {
            throw grafo.nonTrovata(idA2A, idPosizioneDebitoria);
        }
        // Voci e IBAN postali di tutte le pendenze da stampare, in blocco.
        Map<Long, List<VocePendenza>> voci = ibanPostaleResolver.caricaVoci(daStampare);
        for (Pendenza pendenza : daStampare) {
            if (AvvisoPdfPayloadMapper.isPendenzaMbt(voci.get(pendenza.getId()))) {
                throw new AvvisoMbtException(
                        "Stampa posizione debitoria non disponibile: sono presenti dei pagamenti di Marca da Bollo.");
            }
            if (AvvisoPdfPayloadMapper.isAnagraficaDebitoreAssente(pendenza)) {
                throw new AvvisoAnagraficaAssenteException(
                        "Avviso PDF non disponibile: anagrafica del debitore assente.");
            }
        }

        AvvisoPdfPayloadMapper.Risolutore risolutore = risolutore(caricato.pagina(),
                ibanPostaleResolver.risolviTutte(daStampare, voci));
        Consumer<OutputStream> chiamata;
        String operazione = "payment_notice";
        if (opzioni.size() == 1) {
            List<Pendenza> pendenze = opzioni.get(0);
            PaymentNotice payload = pendenze.size() == 1
                    ? pdfPayloadMapper.toPaymentNoticeDocumento(pendenze.get(0), risolutore)
                    : pdfPayloadMapper.toPaymentNoticeRate(pendenze, risolutore);
            chiamata = output -> stampeClient.streamPaymentNotice(payload, output);
        } else if (AvvisoPdfPayloadMapper.sonoRidottoEScontato(opzioni)) {
            var payload = pdfPayloadMapper.toCdsViolation(opzioni, risolutore);
            operazione = "cds_violation";
            chiamata = output -> stampeClient.streamCdsViolation(payload, output);
        } else if (AvvisoPdfPayloadMapper.sonoTutteSoglie(opzioni)) {
            PaymentNotice payload = pdfPayloadMapper.toPaymentNoticeSoglie(
                    opzioni.stream().map(o -> o.get(0)).toList(), risolutore);
            chiamata = output -> stampeClient.streamPaymentNotice(payload, output);
        } else {
            PaymentNotice payload = pdfPayloadMapper.toPaymentNoticeOpzioni(opzioni, risolutore);
            chiamata = output -> stampeClient.streamPaymentNotice(payload, output);
        }

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + idPosizioneDebitoria + ".pdf\"");
        try {
            OutputStream output = response.getOutputStream();
            externalCallMetricsRecorder.record("stampe", operazione, () -> chiamata.accept(output));
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Il debitore di cui si stampa l'avviso: quello indicato, oppure l'unico debitore attivo.
     * Con piu' debitori attivi l'indicazione e' obbligatoria.
     */
    private PdDebitore debitoreDaStampare(PosizioneGrafo.Caricato caricato, String idDebitore) {
        List<PdDebitore> attivi = caricato.pdDebitori().stream().filter(d -> !d.isRimosso()).toList();
        if (idDebitore == null || idDebitore.isBlank()) {
            if (attivi.size() > 1) {
                throw new BadRequestException("la posizione debitoria ha " + attivi.size()
                        + " debitori: 'idDebitore' e' obbligatorio", "idDebitore");
            }
            if (attivi.isEmpty()) {
                throw new AvvisoNonDisponibileException("La posizione debitoria non ha debitori attivi.");
            }
            return attivi.get(0);
        }
        return attivi.stream()
                .filter(d -> d.getDebitore().getIdentificativo().equalsIgnoreCase(idDebitore))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Debitore non trovato nella posizione debitoria."));
    }

    /**
     * Le pendenze pagabili ({@code NON_ESEGUITO}, per numero di rata) di ciascuna opzione del
     * debitore; le opzioni senza pendenze pagabili sono escluse.
     */
    private static List<List<Pendenza>> opzioniStampabili(PosizioneGrafo.Caricato caricato, PdDebitore debitore) {
        List<List<Pendenza>> risultato = new ArrayList<>();
        for (OpzionePagamento opzione : caricato.opzioni()) {
            if (!opzione.getPdDebitore().getId().equals(debitore.getId())) {
                continue;
            }
            List<Pendenza> pagabili = opzione.getPendenze().stream()
                    .filter(p -> p.getStato() == StatoPendenza.NON_ESEGUITO)
                    .sorted(Comparator.comparingInt(Pendenza::getNumeroRata))
                    .toList();
            if (!pagabili.isEmpty()) {
                risultato.add(pagabili);
            }
        }
        return risultato;
    }

    /** Dominio dall'anagrafica gia' risolta, IBAN postale gia' risolto in blocco. */
    private static AvvisoPdfPayloadMapper.Risolutore risolutore(PendenzaAnagrafiche.Pagina pagina,
                                                                Map<Long, IbanAccredito> iban) {
        return new AvvisoPdfPayloadMapper.Risolutore() {
            @Override
            public Dominio dominio(Pendenza pendenza) {
                return pagina.domini().get(pendenza.getIdDominio());
            }

            @Override
            public IbanAccredito iban(Pendenza pendenza) {
                return iban.get(pendenza.getId());
            }
        };
    }

    private static void verificaAcceptCompatibileConPdf(HttpServletRequest request) {
        String accept = request != null ? request.getHeader(HttpHeaders.ACCEPT) : null;
        if (accept == null || accept.isBlank() || accept.contains("*/*")
                || accept.toLowerCase().contains(MediaType.APPLICATION_PDF_VALUE)) {
            return;
        }
        throw new NotAcceptableMediaTypeException(
                "Accept '" + accept + "' non supportato: questo endpoint produce solo application/pdf.");
    }
}
