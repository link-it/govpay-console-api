package it.govpay.console.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.spi.AuthType;
import it.govpay.common.auth.spi.AuthenticatedSubject;
import it.govpay.common.auth.spi.GovpayPrincipalLoader;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Implementazione console-api della SPI {@link GovpayPrincipalLoader}.
 * Carica l'{@code Utenza} locale per principal, valida abilitazione e
 * espone {@code passwordHash} + ruoli (CSV) al verifier della libreria.
 *
 * <p>L'{@link AuthType} ricevuto in input non discrimina la query in questa
 * fase (l'utenza viene cercata sempre per {@code principal}); la libreria
 * lo usera' a livello di chain per disambiguare lo stamping.
 */
@Component
public class ConsoleGovpayPrincipalLoader implements GovpayPrincipalLoader {

    private final UtenzaRepository utenzaRepository;

    public ConsoleGovpayPrincipalLoader(UtenzaRepository utenzaRepository) {
        this.utenzaRepository = utenzaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedSubject load(String principal, AuthType authType) {
        Utenza utenza = utenzaRepository.findByPrincipal(principal).orElse(null);
        if (utenza == null) {
            return null;
        }
        boolean enabled = Boolean.TRUE.equals(utenza.getAbilitato());
        List<String> roles = parseRoles(utenza.getRuoli());
        return new AuthenticatedSubject(utenza.getPrincipal(), utenza.getPassword(), enabled, roles);
    }

    private static List<String> parseRoles(String ruoliCsv) {
        if (ruoliCsv == null || ruoliCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ruoliCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
