package it.govpay.console.applicazione;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.ApplicazioniApi;
import it.govpay.console.model.Applicazione;
import it.govpay.console.model.ApplicazioneCreate;
import it.govpay.console.model.ApplicazioneReplace;
import it.govpay.console.model.JsonPatchOperation;
import it.govpay.console.model.ListApplicazioni200Response;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ApplicazioneController implements ApplicazioniApi {

    private final ApplicazioneService service;

    @Autowired(required = false)
    private HttpServletRequest currentRequest;

    public ApplicazioneController(ApplicazioneService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListApplicazioni200Response> listApplicazioni(Integer page,
                                                                        Integer limit,
                                                                        String sort,
                                                                        Boolean total,
                                                                        String idA2A,
                                                                        String principal,
                                                                        Boolean abilitato) {
        ApplicazioneListQuery query = new ApplicazioneListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                idA2A,
                principal,
                abilitato);
        return ResponseEntity.ok(service.list(query));
    }

    @Override
    public ResponseEntity<Applicazione> getApplicazione(String idA2A) {
        return service.get(idA2A);
    }

    @Override
    public ResponseEntity<Applicazione> createApplicazione(ApplicazioneCreate applicazioneCreate) {
        return service.create(applicazioneCreate, currentRequest);
    }

    @Override
    public ResponseEntity<Applicazione> replaceApplicazione(String idA2A,
                                                            String ifMatch,
                                                            ApplicazioneReplace applicazioneReplace) {
        return service.replace(idA2A, applicazioneReplace, ifMatch, currentRequest);
    }

    @Override
    public ResponseEntity<Applicazione> patchApplicazione(String idA2A,
                                                          String ifMatch,
                                                          List<JsonPatchOperation> jsonPatchOperation) {
        return service.patch(idA2A, jsonPatchOperation, ifMatch, currentRequest);
    }
}
