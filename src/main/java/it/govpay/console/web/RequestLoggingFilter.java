package it.govpay.console.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import it.govpay.common.auth.PrincipalCaptureFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Logga ogni richiesta HTTP a livello INFO con metodo, path, status,
 * principal (se autenticato) e durata. Il {@code requestId} in MDC e' settato
 * da {@link RequestIdFilter}, quindi appare automaticamente nel formato di log.
 *
 * Posizionato DOPO {@link RequestIdFilter} per disporre del correlation-id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String ANONYMOUS = "<anonymous>";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("{} {} status={} duration={}ms principal={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    resolvePrincipal(request));
        }
    }

    private static String resolvePrincipal(HttpServletRequest request) {
        Object captured = request.getAttribute(PrincipalCaptureFilter.REQUEST_ATTRIBUTE);
        if (captured instanceof String s && !s.isBlank()) {
            return s;
        }
        return ANONYMOUS;
    }
}
