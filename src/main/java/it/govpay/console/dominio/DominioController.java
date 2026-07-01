package it.govpay.console.dominio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.DominiApi;
import it.govpay.console.model.Dominio;
import it.govpay.console.model.DominioCreate;
import it.govpay.console.model.DominioReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListDomini200Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class DominioController implements DominiApi {

    private final DominioService service;
    private final DominioLogoService logoService;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    @Autowired(required = false)
    private HttpServletResponse currentResponse;

    public DominioController(DominioService service, DominioLogoService logoService) {
        this.service = service;
        this.logoService = logoService;
    }

    @Override
    public ResponseEntity<ListDomini200Response> listDomini(Integer page,
                                                            Integer limit,
                                                            String sort,
                                                            Boolean total,
                                                            String idDominio,
                                                            String ragioneSociale,
                                                            Boolean abilitato) {
        DominioListQuery query = new DominioListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                idDominio,
                ragioneSociale,
                abilitato);
        return ResponseEntity.ok(service.list(query));
    }

    @Override
    public ResponseEntity<Dominio> getDominio(String idDominio) {
        return service.get(idDominio);
    }

    @Override
    public ResponseEntity<Dominio> createDominio(DominioCreate dominioCreate) {
        return service.create(dominioCreate, currentRequest);
    }

    @Override
    public ResponseEntity<Dominio> replaceDominio(String idDominio,
                                                  String ifMatch,
                                                  DominioReplace dominioReplace) {
        return service.replace(idDominio, dominioReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Dominio> patchDominio(String idDominio,
                                                String ifMatch,
                                                List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idDominio, jsonPatchOperation, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Resource> getDominioLogo(String idDominio) {
        logoService.getLogo(idDominio, currentResponse);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> replaceDominioLogo(String idDominio, Resource body) {
        logoService.putLogo(idDominio, readAllBytes(body), currentRequest);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deleteDominioLogo(String idDominio) {
        logoService.deleteLogo(idDominio, currentRequest);
        return ResponseEntity.noContent().build();
    }

    private static byte[] readAllBytes(Resource body) {
        try {
            return body.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
