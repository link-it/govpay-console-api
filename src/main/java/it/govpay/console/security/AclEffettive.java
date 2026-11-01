package it.govpay.console.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;

/**
 * ACL effettive di un'utenza: quelle dirette ({@code id_utenza}) piu' le ACL di
 * definizione ({@code id_utenza IS NULL}) di ciascun ruolo del CSV
 * {@code utenze.ruoli}, come nel merge fatto dal core.
 *
 * <p>Unico punto di calcolo per l'autorizzazione degli endpoint
 * ({@link AclAuthorizer}) e per le {@code acl} di {@code GET /profilo}, che la
 * console usa per mostrare le sezioni: calcolate separatamente, le due viste
 * divergevano (issue #122).
 */
@Component
public class AclEffettive {

    private final AclRepository aclRepository;

    public AclEffettive(AclRepository aclRepository) {
        this.aclRepository = aclRepository;
    }

    public List<Acl> di(Utenza utenza) {
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
