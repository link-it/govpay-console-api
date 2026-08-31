package it.govpay.console.tipopendenza;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.TipiPendenzaApi;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListTipiPendenza200Response;
import it.govpay.console.model.TipoPendenza;
import it.govpay.console.model.TipoPendenzaCreate;
import it.govpay.console.model.TipoPendenzaReplace;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class TipoPendenzaController implements TipiPendenzaApi {

    private final TipoPendenzaService service;
    private final HttpServletRequest currentRequest;

    public TipoPendenzaController(TipoPendenzaService service,
                                  HttpServletRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @Override
    public ResponseEntity<ListTipiPendenza200Response> listTipiPendenza(Integer page,
                                                                        Integer limit,
                                                                        String sort,
                                                                        Boolean total,
                                                                        String idTipoPendenza,
                                                                        String descrizione,
                                                                        Boolean abilitato,
                                                                        Boolean form,
                                                                        Boolean trasformazione,
                                                                        String nonAssociati) {
        TipoPendenzaListQuery query = new TipoPendenzaListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                idTipoPendenza,
                descrizione,
                abilitato,
                form,
                trasformazione,
                nonAssociati);
        return ResponseEntity.ok(service.list(query));
    }

    @Override
    public ResponseEntity<TipoPendenza> getTipoPendenza(String idTipoPendenza) {
        return service.get(idTipoPendenza);
    }

    @Override
    public ResponseEntity<TipoPendenza> createTipoPendenza(TipoPendenzaCreate tipoPendenzaCreate) {
        return service.create(tipoPendenzaCreate, currentRequest);
    }

    @Override
    public ResponseEntity<TipoPendenza> replaceTipoPendenza(String idTipoPendenza,
                                                            String ifMatch,
                                                            TipoPendenzaReplace tipoPendenzaReplace) {
        return service.replace(idTipoPendenza, tipoPendenzaReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<TipoPendenza> patchTipoPendenza(String idTipoPendenza,
                                                          String ifMatch,
                                                          List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idTipoPendenza, jsonPatchOperation, ifMatch, currentRequest);
    }
}
