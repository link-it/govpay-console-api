package it.govpay.console.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.common.DirittiCodec;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Verifica i diritti ACL dell'utenza autenticata. Un diritto su un servizio
 * e' concesso se compare in una ACL diretta dell'utenza oppure in una ACL di
 * definizione ({@code id_utenza IS NULL}) di uno dei suoi ruoli, come nel
 * merge fatto dal core.
 */
@Service
public class AclAuthorizer {

    private final UtenzaRepository utenzaRepository;
    private final AclRepository aclRepository;

    public AclAuthorizer(UtenzaRepository utenzaRepository, AclRepository aclRepository) {
        this.utenzaRepository = utenzaRepository;
        this.aclRepository = aclRepository;
    }

    /**
     * Verifica che l'utenza autenticata abbia il diritto di scrittura sul
     * servizio indicato; altrimenti lancia {@link AccessDeniedException}
     * (resa come 403 problem+json).
     */
    @Transactional(readOnly = true)
    public void requireScrittura(AclServizio servizio) {
        Utenza utenza = utenzaAutenticata();
        boolean autorizzata = aclDellUtenza(utenza).stream()
                .anyMatch(acl -> servizio.getValue().equals(acl.getServizio())
                        && DirittiCodec.parse(acl.getDiritti())
                                .contains(it.govpay.console.model.Acl.AutorizzazioniEnum.W));
        if (!autorizzata) {
            throw new AccessDeniedException("L'utenza '" + utenza.getPrincipal()
                    + "' non dispone del diritto di scrittura sul servizio '" + servizio.getValue() + "'.");
        }
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

    private List<Acl> aclDellUtenza(Utenza utenza) {
        List<Acl> acls = new ArrayList<>(aclRepository.findByIdUtenza(utenza.getId()));
        String csv = utenza.getRuoli();
        if (csv != null && !csv.isBlank()) {
            for (String ruolo : csv.split(",")) {
                String trimmed = ruolo.trim();
                if (!trimmed.isEmpty()) {
                    acls.addAll(aclRepository.findByRuoloAndIdUtenzaIsNull(trimmed));
                }
            }
        }
        return acls;
    }
}
