package it.govpay.console.tipopendenzadominio;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.TipiPendenzaDominioApi;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListTipiPendenzaDominio200Response;
import it.govpay.console.model.TipoPendenzaDominio;
import it.govpay.console.model.TipoPendenzaDominioCreate;
import it.govpay.console.model.TipoPendenzaDominioReplace;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class TipoPendenzaDominioController implements TipiPendenzaDominioApi {

    private final TipoPendenzaDominioService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public TipoPendenzaDominioController(TipoPendenzaDominioService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListTipiPendenzaDominio200Response> listTipiPendenzaDominio(String idDominio,
                                                                                      Integer page,
                                                                                      Integer limit,
                                                                                      String sort,
                                                                                      Boolean total,
                                                                                      String idTipoPendenza,
                                                                                      String descrizione,
                                                                                      Boolean abilitato) {
        TipoPendenzaDominioListQuery query = new TipoPendenzaDominioListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                idTipoPendenza,
                descrizione,
                abilitato);
        return ResponseEntity.ok(service.list(idDominio, query));
    }

    @Override
    public ResponseEntity<TipoPendenzaDominio> getTipoPendenzaDominio(String idDominio, String idTipoPendenza) {
        return service.get(idDominio, idTipoPendenza);
    }

    @Override
    public ResponseEntity<TipoPendenzaDominio> createTipoPendenzaDominio(String idDominio,
                                                                         TipoPendenzaDominioCreate tipoPendenzaDominioCreate) {
        return service.create(idDominio, tipoPendenzaDominioCreate, currentRequest);
    }

    @Override
    public ResponseEntity<TipoPendenzaDominio> replaceTipoPendenzaDominio(String idDominio,
                                                                          String idTipoPendenza,
                                                                          String ifMatch,
                                                                          TipoPendenzaDominioReplace tipoPendenzaDominioReplace) {
        return service.replace(idDominio, idTipoPendenza, tipoPendenzaDominioReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<TipoPendenzaDominio> patchTipoPendenzaDominio(String idDominio,
                                                                        String idTipoPendenza,
                                                                        String ifMatch,
                                                                        List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idDominio, idTipoPendenza, jsonPatchOperation, ifMatch, currentRequest);
    }
}
