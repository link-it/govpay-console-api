package it.govpay.console.metrics;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Pubblica il breakdown della request API in tempo esterno/interno con tag
 * allineati a {@code http.server.requests}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiTimingMetricsFilter extends OncePerRequestFilter {

    private static final String UNKNOWN = "UNKNOWN";
    private static final String OTHER = "OTHER";
    private static final Set<String> KNOWN_HTTP_METHODS = Set.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");

    private final ObjectProvider<ExternalCallMetricsContext> contextProvider;
    private final MeterRegistry meterRegistry;

    public ApiTimingMetricsFilter(ObjectProvider<ExternalCallMetricsContext> contextProvider,
                                  MeterRegistry meterRegistry) {
        this.contextProvider = contextProvider;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (DispatcherType.ERROR.equals(request.getDispatcherType())) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/actuator");
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        Throwable error = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error e) {
            error = e;
            throw e;
        } finally {
            long total = System.nanoTime() - start;
            ExternalCallMetricsContext context = contextProvider.getIfAvailable();
            long external = context != null ? context.externalNanos() : 0L;
            long internal = Math.max(0L, total - external);
            int status = status(response, error);

            Tags tags = Tags.of(
                    "method", method(request),
                    "uri", bestMatchingPattern(request),
                    "status", Integer.toString(status),
                    "outcome", outcome(status));

            meterRegistry.timer("govpay.api.external.duration", tags)
                    .record(external, TimeUnit.NANOSECONDS);
            meterRegistry.timer("govpay.api.internal.duration", tags)
                    .record(internal, TimeUnit.NANOSECONDS);
        }
    }

    private static String bestMatchingPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return UNKNOWN;
    }

    private static String method(HttpServletRequest request) {
        String method = request.getMethod();
        if (method != null && KNOWN_HTTP_METHODS.contains(method)) {
            return method;
        }
        return OTHER;
    }

    private static int status(HttpServletResponse response, Throwable error) {
        int status = response.getStatus();
        if (error != null && status < 400) {
            return 500;
        }
        return status;
    }

    private static String outcome(int status) {
        if (status >= 100 && status < 200) {
            return "INFORMATIONAL";
        }
        if (status >= 200 && status < 300) {
            return "SUCCESS";
        }
        if (status >= 300 && status < 400) {
            return "REDIRECTION";
        }
        if (status >= 400 && status < 500) {
            return "CLIENT_ERROR";
        }
        if (status >= 500 && status < 600) {
            return "SERVER_ERROR";
        }
        return UNKNOWN;
    }
}
