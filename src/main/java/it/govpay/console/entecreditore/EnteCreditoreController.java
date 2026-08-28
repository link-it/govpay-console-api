package it.govpay.console.entecreditore;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.EntiCreditoriApi;
import it.govpay.console.model.EnteCreditore;
import it.govpay.console.model.ListEntiCreditori200Response;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class EnteCreditoreController implements EntiCreditoriApi {

    private final EnteCreditoreService service;
    private final HttpServletRequest currentRequest;

    public EnteCreditoreController(EnteCreditoreService service,
                                   HttpServletRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @Override
    public ResponseEntity<ListEntiCreditori200Response> listEntiCreditori(Integer page,
                                                                          Integer limit,
                                                                          String sort,
                                                                          Boolean total,
                                                                          String search) {
        EnteCreditoreListQuery query = new EnteCreditoreListQuery(
                page == null ? 1 : page,
                limit == null ? 25 : limit,
                sort,
                total,
                search);
        return ResponseEntity.ok(service.list(query, currentRequest));
    }

    @Override
    public ResponseEntity<EnteCreditore> getEnteCreditore(String taxCode) {
        return ResponseEntity.ok(service.get(taxCode, currentRequest));
    }
}
