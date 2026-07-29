package it.govpay.console.operazioni;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ManutenzioneOperazioniApi;
import it.govpay.console.model.Esecuzione;
import it.govpay.console.model.ListEsecuzioni200Response;
import it.govpay.console.model.Operazione;
import it.govpay.console.model.RichiestaEsecuzione;
import it.govpay.console.model.StatoEsecuzione;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class OperazioniController implements ManutenzioneOperazioniApi {

    private final OperazioniService service;
    private final OperazioneEsecuzioneService esecuzioneService;
    private final OperazioneEsecuzioniService esecuzioniService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public OperazioniController(OperazioniService service, OperazioneEsecuzioneService esecuzioneService,
            OperazioneEsecuzioniService esecuzioniService) {
        this.service = service;
        this.esecuzioneService = esecuzioneService;
        this.esecuzioniService = esecuzioniService;
    }

    @Override
    public ResponseEntity<List<Operazione>> listOperazioni() {
        return ResponseEntity.ok(service.getCatalogo());
    }

    @Override
    public ResponseEntity<Esecuzione> avviaEsecuzione(String idOperazione, RichiestaEsecuzione richiestaEsecuzione) {
        boolean force = richiestaEsecuzione != null && Boolean.TRUE.equals(richiestaEsecuzione.getForce());
        return esecuzioneService.avviaEsecuzione(idOperazione, force, currentRequest);
    }

    @Override
    public ResponseEntity<ListEsecuzioni200Response> listEsecuzioni(String idOperazione, Integer page, Integer limit,
            Boolean total, StatoEsecuzione stato, OffsetDateTime dataInizioMin, OffsetDateTime dataInizioMax) {
        ListEsecuzioni200Response body = esecuzioniService.list(idOperazione, stato, dataInizioMin, dataInizioMax,
                page, limit, Boolean.TRUE.equals(total));
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(body);
    }

    @Override
    public ResponseEntity<Esecuzione> getEsecuzione(String idOperazione, String idEsecuzione) {
        Esecuzione body = esecuzioniService.dettaglio(idOperazione, idEsecuzione);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(body);
    }

    @Override
    public ResponseEntity<Void> annullaEsecuzione(String idOperazione, String idEsecuzione) {
        esecuzioniService.annullaEsecuzione(idOperazione, idEsecuzione);
        return ResponseEntity.accepted().build();
    }
}
