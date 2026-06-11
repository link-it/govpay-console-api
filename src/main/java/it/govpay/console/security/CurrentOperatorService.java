package it.govpay.console.security;

import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;

@Service
public class CurrentOperatorService {

    private final UtenzaRepository utenzaRepository;
    private final OperatoreRepository operatoreRepository;
    private final UtenzaDominioRepository utenzaDominioRepository;

    public CurrentOperatorService(UtenzaRepository utenzaRepository,
                                  OperatoreRepository operatoreRepository,
                                  UtenzaDominioRepository utenzaDominioRepository) {
        this.utenzaRepository = utenzaRepository;
        this.operatoreRepository = operatoreRepository;
        this.utenzaDominioRepository = utenzaDominioRepository;
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
        List<Long> idDominiVisibili = tuttiIDomini
                ? List.of()
                : utenzaDominioRepository.findByIdUtenza(utenza.getId()).stream()
                        .map(ud -> ud.getIdDominio())
                        .filter(id -> id != null)
                        .distinct()
                        .toList();

        return new OperatoreCorrente(
                utenza.getPrincipal(),
                utenza.getId(),
                operatore != null ? operatore.getId() : null,
                operatore != null ? operatore.getNome() : null,
                tuttiIDomini,
                idDominiVisibili);
    }
}
