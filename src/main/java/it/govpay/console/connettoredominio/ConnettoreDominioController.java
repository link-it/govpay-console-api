package it.govpay.console.connettoredominio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ConnettoriDominioApi;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ConnettoreDominioGovpay;
import it.govpay.console.model.ConnettoreDominioHypersicApk;
import it.govpay.console.model.ConnettoreDominioMaggioliJppa;
import it.govpay.console.model.ConnettoreDominioMypivot;
import it.govpay.console.model.ConnettoreDominioSecim;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ConnettoreDominioController implements ConnettoriDominioApi {

    private final ConnettoreDominioService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public ConnettoreDominioController(ConnettoreDominioService service) {
        this.service = service;
    }

    // --- mypivot ---
    @Override
    public ResponseEntity<ConnettoreDominioMypivot> getConnettoreDominioMypivot(String idDominio) {
        return service.get(idDominio, ConnettoreDominioCanale.MYPIVOT, ConnettoreDominioMypivot.class);
    }

    @Override
    public ResponseEntity<ConnettoreDominioMypivot> replaceConnettoreDominioMypivot(
            String idDominio, String ifMatch, ConnettoreDominioMypivot body) {
        return service.replace(idDominio, ConnettoreDominioCanale.MYPIVOT, body, ifMatch,
                ConnettoreDominioMypivot.class, currentRequest);
    }

    // --- secim ---
    @Override
    public ResponseEntity<ConnettoreDominioSecim> getConnettoreDominioSecim(String idDominio) {
        return service.get(idDominio, ConnettoreDominioCanale.SECIM, ConnettoreDominioSecim.class);
    }

    @Override
    public ResponseEntity<ConnettoreDominioSecim> replaceConnettoreDominioSecim(
            String idDominio, String ifMatch, ConnettoreDominioSecim body) {
        return service.replace(idDominio, ConnettoreDominioCanale.SECIM, body, ifMatch,
                ConnettoreDominioSecim.class, currentRequest);
    }

    // --- govpay ---
    @Override
    public ResponseEntity<ConnettoreDominioGovpay> getConnettoreDominioGovpay(String idDominio) {
        return service.get(idDominio, ConnettoreDominioCanale.GOVPAY, ConnettoreDominioGovpay.class);
    }

    @Override
    public ResponseEntity<ConnettoreDominioGovpay> replaceConnettoreDominioGovpay(
            String idDominio, String ifMatch, ConnettoreDominioGovpay body) {
        return service.replace(idDominio, ConnettoreDominioCanale.GOVPAY, body, ifMatch,
                ConnettoreDominioGovpay.class, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiConnettoreDominioGovpay(
            String idDominio, ConnettoreCredenziali body) {
        return service.putCredenziali(idDominio, ConnettoreDominioCanale.GOVPAY, body, currentRequest);
    }

    // --- hypersic-apk ---
    @Override
    public ResponseEntity<ConnettoreDominioHypersicApk> getConnettoreDominioHypersicApk(String idDominio) {
        return service.get(idDominio, ConnettoreDominioCanale.HYPER_SIC_APKAPPA, ConnettoreDominioHypersicApk.class);
    }

    @Override
    public ResponseEntity<ConnettoreDominioHypersicApk> replaceConnettoreDominioHypersicApk(
            String idDominio, String ifMatch, ConnettoreDominioHypersicApk body) {
        return service.replace(idDominio, ConnettoreDominioCanale.HYPER_SIC_APKAPPA, body, ifMatch,
                ConnettoreDominioHypersicApk.class, currentRequest);
    }

    // --- maggioli-jppa ---
    @Override
    public ResponseEntity<ConnettoreDominioMaggioliJppa> getConnettoreDominioMaggioliJppa(String idDominio) {
        return service.get(idDominio, ConnettoreDominioCanale.MAGGIOLI_JPPA, ConnettoreDominioMaggioliJppa.class);
    }

    @Override
    public ResponseEntity<ConnettoreDominioMaggioliJppa> replaceConnettoreDominioMaggioliJppa(
            String idDominio, String ifMatch, ConnettoreDominioMaggioliJppa body) {
        return service.replace(idDominio, ConnettoreDominioCanale.MAGGIOLI_JPPA, body, ifMatch,
                ConnettoreDominioMaggioliJppa.class, currentRequest);
    }

    @Override
    public ResponseEntity<Void> putCredenzialiConnettoreDominioMaggioliJppa(
            String idDominio, ConnettoreCredenziali body) {
        return service.putCredenziali(idDominio, ConnettoreDominioCanale.MAGGIOLI_JPPA, body, currentRequest);
    }
}
