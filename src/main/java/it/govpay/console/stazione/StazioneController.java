package it.govpay.console.stazione;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.StazioniApi;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListStazioni200Response;
import it.govpay.console.model.Stazione;
import it.govpay.console.model.StazioneCreate;
import it.govpay.console.model.StazioneReplace;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class StazioneController implements StazioniApi {

    private final StazioneService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public StazioneController(StazioneService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListStazioni200Response> listStazioni(String idIntermediario,
                                                                Integer page,
                                                                Integer limit,
                                                                String sort,
                                                                Boolean total,
                                                                String codStazione,
                                                                Boolean abilitato) {
        StazioneListQuery query = new StazioneListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                codStazione,
                abilitato);
        return ResponseEntity.ok(service.list(idIntermediario, query));
    }

    @Override
    public ResponseEntity<Stazione> getStazione(String idIntermediario, String idStazione) {
        return service.get(idIntermediario, idStazione);
    }

    @Override
    public ResponseEntity<Stazione> createStazione(String idIntermediario, StazioneCreate stazioneCreate) {
        return service.create(idIntermediario, stazioneCreate, currentRequest);
    }

    @Override
    public ResponseEntity<Stazione> replaceStazione(String idIntermediario,
                                                    String idStazione,
                                                    String ifMatch,
                                                    StazioneReplace stazioneReplace) {
        return service.replace(idIntermediario, idStazione, stazioneReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Stazione> patchStazione(String idIntermediario,
                                                  String idStazione,
                                                  String ifMatch,
                                                  List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idIntermediario, idStazione, jsonPatchOperation, ifMatch, currentRequest);
    }
}
