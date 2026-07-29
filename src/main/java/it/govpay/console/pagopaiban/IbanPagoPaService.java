package it.govpay.console.pagopaiban;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.model.IbanPagoPa;
import it.govpay.console.repository.IbanCacheRepository;

@Service
public class IbanPagoPaService {

    private static final Logger log = LoggerFactory.getLogger(IbanPagoPaService.class);

    private final IbanCacheRepository repository;
    private final IbanPagoPaMapper mapper;

    public IbanPagoPaService(IbanCacheRepository repository, IbanPagoPaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<IbanPagoPa> list(String idDominio) {
        log.debug("listIbanPagopa idDominio={}", idDominio);
        return repository.findByCodDominioOrderByIbanAsc(idDominio).stream()
                .map(mapper::toDto)
                .toList();
    }
}
