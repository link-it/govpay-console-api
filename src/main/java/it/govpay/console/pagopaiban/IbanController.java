package it.govpay.console.pagopaiban;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.api.IbanApi;
import it.govpay.console.model.IbanPagoPa;

@RestController
public class IbanController implements IbanApi {

    private final IbanPagoPaService service;

    public IbanController(IbanPagoPaService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<IbanPagoPa>> listIbanPagopa(String idDominio) {
        return ResponseEntity.ok(service.list(idDominio));
    }
}
