package it.govpay.console.connettore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ConnettoriApi;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ConnettoreIntermediarioPagopa;
import it.govpay.console.model.ConnettoreIntermediarioPagopaAca;
import it.govpay.console.model.ConnettoreIntermediarioPagopaBackofficeEc;
import it.govpay.console.model.ConnettoreIntermediarioPagopaFr;
import it.govpay.console.model.ConnettoreIntermediarioPagopaGpd;
import it.govpay.console.model.ConnettoreIntermediarioPagopaRecuperoRt;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ConnettoreController implements ConnettoriApi {

    private final ConnettoreService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public ConnettoreController(ConnettoreService service) {
        this.service = service;
    }

    // --- pagopa ---
    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopa> getConnettorePagopa(String idIntermediario) {
        return service.get(idIntermediario, ConnettoreCanale.PAGOPA, ConnettoreIntermediarioPagopa.class);
    }

    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopa> replaceConnettorePagopa(
            String idIntermediario, String ifMatch, ConnettoreIntermediarioPagopa body) {
        return service.replace(idIntermediario, ConnettoreCanale.PAGOPA, body, ifMatch,
                ConnettoreIntermediarioPagopa.class, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiConnettorePagopa(
            String idIntermediario, ConnettoreCredenziali body) {
        return service.putCredenziali(idIntermediario, ConnettoreCanale.PAGOPA, body, currentRequest);
    }

    // --- pagopa-aca ---
    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaAca> getConnettorePagopaAca(String idIntermediario) {
        return service.get(idIntermediario, ConnettoreCanale.ACA, ConnettoreIntermediarioPagopaAca.class);
    }

    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaAca> replaceConnettorePagopaAca(
            String idIntermediario, String ifMatch, ConnettoreIntermediarioPagopaAca body) {
        return service.replace(idIntermediario, ConnettoreCanale.ACA, body, ifMatch,
                ConnettoreIntermediarioPagopaAca.class, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiConnettorePagopaAca(
            String idIntermediario, ConnettoreCredenziali body) {
        return service.putCredenziali(idIntermediario, ConnettoreCanale.ACA, body, currentRequest);
    }

    // --- pagopa-gpd ---
    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaGpd> getConnettorePagopaGpd(String idIntermediario) {
        return service.get(idIntermediario, ConnettoreCanale.GPD, ConnettoreIntermediarioPagopaGpd.class);
    }

    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaGpd> replaceConnettorePagopaGpd(
            String idIntermediario, String ifMatch, ConnettoreIntermediarioPagopaGpd body) {
        return service.replace(idIntermediario, ConnettoreCanale.GPD, body, ifMatch,
                ConnettoreIntermediarioPagopaGpd.class, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiConnettorePagopaGpd(
            String idIntermediario, ConnettoreCredenziali body) {
        return service.putCredenziali(idIntermediario, ConnettoreCanale.GPD, body, currentRequest);
    }

    // --- pagopa-fr ---
    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaFr> getConnettorePagopaFr(String idIntermediario) {
        return service.get(idIntermediario, ConnettoreCanale.FR, ConnettoreIntermediarioPagopaFr.class);
    }

    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaFr> replaceConnettorePagopaFr(
            String idIntermediario, String ifMatch, ConnettoreIntermediarioPagopaFr body) {
        return service.replace(idIntermediario, ConnettoreCanale.FR, body, ifMatch,
                ConnettoreIntermediarioPagopaFr.class, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiConnettorePagopaFr(
            String idIntermediario, ConnettoreCredenziali body) {
        return service.putCredenziali(idIntermediario, ConnettoreCanale.FR, body, currentRequest);
    }

    // --- pagopa-backoffice-ec ---
    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaBackofficeEc> getConnettorePagopaBackofficeEc(String idIntermediario) {
        return service.get(idIntermediario, ConnettoreCanale.BACKOFFICE_EC, ConnettoreIntermediarioPagopaBackofficeEc.class);
    }

    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaBackofficeEc> replaceConnettorePagopaBackofficeEc(
            String idIntermediario, String ifMatch, ConnettoreIntermediarioPagopaBackofficeEc body) {
        return service.replace(idIntermediario, ConnettoreCanale.BACKOFFICE_EC, body, ifMatch,
                ConnettoreIntermediarioPagopaBackofficeEc.class, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiConnettorePagopaBackofficeEc(
            String idIntermediario, ConnettoreCredenziali body) {
        return service.putCredenziali(idIntermediario, ConnettoreCanale.BACKOFFICE_EC, body, currentRequest);
    }

    // --- pagopa-recupero-rt ---
    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaRecuperoRt> getConnettorePagopaRecuperoRt(String idIntermediario) {
        return service.get(idIntermediario, ConnettoreCanale.RECUPERO_RT, ConnettoreIntermediarioPagopaRecuperoRt.class);
    }

    @Override
    public ResponseEntity<ConnettoreIntermediarioPagopaRecuperoRt> replaceConnettorePagopaRecuperoRt(
            String idIntermediario, String ifMatch, ConnettoreIntermediarioPagopaRecuperoRt body) {
        return service.replace(idIntermediario, ConnettoreCanale.RECUPERO_RT, body, ifMatch,
                ConnettoreIntermediarioPagopaRecuperoRt.class, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiConnettorePagopaRecuperoRt(
            String idIntermediario, ConnettoreCredenziali body) {
        return service.putCredenziali(idIntermediario, ConnettoreCanale.RECUPERO_RT, body, currentRequest);
    }
}
