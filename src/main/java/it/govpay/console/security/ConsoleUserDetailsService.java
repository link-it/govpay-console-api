package it.govpay.console.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.UtenzaRepository;

@Service
public class ConsoleUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleUserDetailsService.class);

    private static final String ROLE_PREFIX = "ROLE_";

    private final UtenzaRepository utenzaRepository;

    public ConsoleUserDetailsService(UtenzaRepository utenzaRepository) {
        this.utenzaRepository = utenzaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String principal) {
        Utenza utenza = utenzaRepository.findByPrincipal(principal)
                .orElseThrow(() -> new UsernameNotFoundException("Principal non trovato: " + principal));
        if (Boolean.FALSE.equals(utenza.getAbilitato())) {
            throw new UsernameNotFoundException("Utenza disabilitata: " + principal);
        }
        log.debug("loadUserByUsername principal={} ruoli={}", principal, utenza.getRuoli());
        return User.builder()
                .username(utenza.getPrincipal())
                .password(utenza.getPassword() != null ? utenza.getPassword() : "")
                .authorities(parseAuthorities(utenza.getRuoli()))
                .disabled(false)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .build();
    }

    private static Collection<? extends GrantedAuthority> parseAuthorities(String ruoliCsv) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (ruoliCsv == null || ruoliCsv.isBlank()) {
            return authorities;
        }
        for (String raw : ruoliCsv.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String name = trimmed.startsWith(ROLE_PREFIX) ? trimmed : ROLE_PREFIX + trimmed;
            authorities.add(new SimpleGrantedAuthority(name));
        }
        return authorities;
    }
}
