package it.govpay.console.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cattura il nome del principal autenticato e lo salva come attributo della request,
 * sopravvivendo al clear del SecurityContext. Cosi' i filter posti prima della
 * SecurityFilterChain (es. {@code RequestLoggingFilter}) possono leggerlo al finally.
 *
 * NON e' un {@code @Component}: viene istanziato manualmente in
 * {@code SecurityConfig} e registrato solo dentro la SecurityFilterChain
 * tramite {@code addFilterAfter(...)}, per evitare doppia registrazione.
 */
public class PrincipalCaptureFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = "authPrincipal";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            request.setAttribute(REQUEST_ATTRIBUTE, auth.getName());
        }
        filterChain.doFilter(request, response);
    }
}
