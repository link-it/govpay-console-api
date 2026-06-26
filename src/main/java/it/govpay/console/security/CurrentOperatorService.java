package it.govpay.console.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;

@Service
public class CurrentOperatorService {

    private static final Logger log = LoggerFactory.getLogger(CurrentOperatorService.class);

    private final UtenzaRepository utenzaRepository;
    private final OperatoreRepository operatoreRepository;
    private final UtenzaDominioRepository utenzaDominioRepository;
    private final UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;

    public CurrentOperatorService(UtenzaRepository utenzaRepository,
                                  OperatoreRepository operatoreRepository,
                                  UtenzaDominioRepository utenzaDominioRepository,
                                  UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository) {
        this.utenzaRepository = utenzaRepository;
        this.operatoreRepository = operatoreRepository;
        this.utenzaDominioRepository = utenzaDominioRepository;
        this.utenzaTipoVersamentoRepository = utenzaTipoVersamentoRepository;
    }

    /**
     * Risolve il principal corrente in {@link OperatoreCorrente}. Lancia
     * {@link IllegalStateException} se non c'e' un utente autenticato (i.e.
     * va invocato da codice dietro la SecurityFilterChain).
     */
    @Transactional(readOnly = true)
    public OperatoreCorrente get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("Nessun utente autenticato nel SecurityContext.");
        }
        String principal = authentication.getName();
        Utenza utenza = utenzaRepository.findByPrincipal(principal)
                .orElseThrow(() -> new IllegalStateException(
                        "Principal autenticato non trovato in utenze: " + principal));

        Operatore operatore = operatoreRepository.findByIdUtenza(utenza.getId()).orElse(null);

        boolean tuttiIDomini = Boolean.TRUE.equals(utenza.getAutorizzazioneDominiStar());
        Set<Long> idDominiInteri = Set.of();
        Set<Long> idUoVisibili = Set.of();
        if (!tuttiIDomini) {
            var ud = utenzaDominioRepository.findByIdUtenza(utenza.getId());
            idDominiInteri = ud.stream()
                    .filter(r -> r.getIdUo() == null && r.getIdDominio() != null)
                    .map(UtenzaDominio::getIdDominio)
                    .collect(Collectors.toUnmodifiableSet());
            idUoVisibili = ud.stream()
                    .filter(r -> r.getIdUo() != null)
                    .map(UtenzaDominio::getIdUo)
                    .collect(Collectors.toUnmodifiableSet());
        }

        boolean tuttiITipiVersamento = Boolean.TRUE.equals(utenza.getAutorizzazioneTipiVersStar());
        Set<Long> idTipiVersamentoVisibili = tuttiITipiVersamento
                ? Set.of()
                : utenzaTipoVersamentoRepository.findByIdUtenza(utenza.getId()).stream()
                        .map(r -> r.getIdTipoVersamento())
                        .collect(Collectors.toUnmodifiableSet());

        OperatoreCorrente result = new OperatoreCorrente(
                utenza.getPrincipal(),
                utenza.getId(),
                operatore != null ? operatore.getId() : null,
                operatore != null ? operatore.getNome() : null,
                tuttiIDomini,
                idDominiInteri,
                idUoVisibili,
                tuttiITipiVersamento,
                idTipiVersamentoVisibili);
        log.debug("operatore corrente principal={} idOperatore={} tuttiIDomini={} dominiInteri={} uoVisibili={} "
                        + "tuttiITipiVersamento={} tipiVersamentoVisibili={}",
                result.principal(), result.idOperatore(), result.tuttiIDomini(),
                result.idDominiInteri(), result.idUoVisibili(),
                result.tuttiITipiVersamento(), result.idTipiVersamentoVisibili());
        return result;
    }
}
