package it.govpay.console.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.common.DirittiCodec;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Verifica i diritti ACL dell'utenza autenticata. Un diritto su un servizio
 * e' concesso se compare in una delle sue {@link AclEffettive ACL effettive}:
 * dirette o di uno dei suoi ruoli.
 */
@Service
public class AclAuthorizer {

    private final UtenzaRepository utenzaRepository;
    private final AclEffettive aclEffettive;

    public AclAuthorizer(UtenzaRepository utenzaRepository, AclEffettive aclEffettive) {
        this.utenzaRepository = utenzaRepository;
        this.aclEffettive = aclEffettive;
    }

    /**
     * Verifica che l'utenza autenticata abbia il diritto di scrittura sul
     * servizio indicato; altrimenti lancia {@link AccessDeniedException}
     * (resa come 403 problem+json).
     */
    @Transactional(readOnly = true)
    public void requireScrittura(AclServizio servizio) {
        require(servizio, it.govpay.console.model.Acl.AutorizzazioniEnum.W, "scrittura");
    }

    /**
     * Verifica che l'utenza autenticata abbia il diritto di lettura sul
     * servizio indicato; altrimenti lancia {@link AccessDeniedException}
     * (resa come 403 problem+json).
     */
    @Transactional(readOnly = true)
    public void requireLettura(AclServizio servizio) {
        require(servizio, it.govpay.console.model.Acl.AutorizzazioniEnum.R, "lettura");
    }

    /**
     * Verifica non bloccante del diritto di lettura sul servizio indicato:
     * restituisce {@code false} invece di lanciare {@link AccessDeniedException}.
     */
    @Transactional(readOnly = true)
    public boolean haLettura(AclServizio servizio) {
        return autorizzato(servizio, it.govpay.console.model.Acl.AutorizzazioniEnum.R);
    }

    private void require(AclServizio servizio, it.govpay.console.model.Acl.AutorizzazioniEnum diritto, String label) {
        if (!autorizzato(servizio, diritto)) {
            throw new AccessDeniedException("L'utenza '" + utenzaAutenticata().getPrincipal()
                    + "' non dispone del diritto di " + label + " sul servizio '" + servizio.getValue() + "'.");
        }
    }

    private boolean autorizzato(AclServizio servizio, it.govpay.console.model.Acl.AutorizzazioniEnum diritto) {
        Utenza utenza = utenzaAutenticata();
        return aclEffettive.di(utenza).stream()
                .anyMatch(acl -> servizio.getValue().equals(acl.getServizio())
                        && DirittiCodec.parse(acl.getDiritti()).contains(diritto));
    }

    private Utenza utenzaAutenticata() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("Nessun utente autenticato nel SecurityContext.");
        }
        return utenzaRepository.findByPrincipal(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Principal autenticato non trovato in utenze: " + authentication.getName()));
    }
}
