package it.govpay.console.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ExternalCallMetricsRecorderTest {

    @Test
    void recordsSuccessOutcomeAndAccumulatesExternalDuration() {
        Fixture fixture = new Fixture();

        fixture.recorder.record("stampe", "receipt", () -> {
            // successful call
        });

        assertThat(fixture.timerCount("success")).isEqualTo(1L);
        assertThat(fixture.context.externalNanos()).isPositive();
    }

    @Test
    void recordsExternalMetricEvenWhenRequestScopeIsNotActive() {
        ExternalCallMetricsContext context = mock(ExternalCallMetricsContext.class);
        ScopeNotActiveException scopeError = new ScopeNotActiveException(
                "request", "externalCallMetricsContext", new IllegalStateException("no request"));
        when(context.externalNanos()).thenThrow(scopeError);
        org.mockito.Mockito.doThrow(scopeError).when(context).record(org.mockito.ArgumentMatchers.anyLong());

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExternalCallMetricsRecorder recorder = new ExternalCallMetricsRecorder(provider(context), registry);

        recorder.record("stampe", "receipt", () -> {
            // successful call outside HTTP request scope
        });

        assertThat(timerCount(registry, "success")).isEqualTo(1L);
    }

    @Test
    void classifiesSocketTimeoutAsTimeout() {
        Fixture fixture = new Fixture();
        RuntimeException error = new ResourceAccessException(
                "Read timed out", new SocketTimeoutException("Read timed out"));

        assertThatThrownBy(() -> fixture.recorder.record("stampe", "receipt", () -> {
            throw error;
        })).isSameAs(error);

        assertThat(fixture.timerCount("timeout")).isEqualTo(1L);
    }

    @Test
    void classifiesHttpTimeoutAsTimeout() {
        Fixture fixture = new Fixture();
        RuntimeException error = new RuntimeException(new HttpTimeoutException("deadline"));

        assertThatThrownBy(() -> fixture.recorder.record("stampe", "receipt", () -> {
            throw error;
        })).isSameAs(error);

        assertThat(fixture.timerCount("timeout")).isEqualTo(1L);
    }

    @Test
    void classifiesNonTimeoutResourceAccessAsIoError() {
        Fixture fixture = new Fixture();
        RuntimeException error = new ResourceAccessException(
                "Connection refused", new ConnectException("Connection refused"));

        assertThatThrownBy(() -> fixture.recorder.record("stampe", "receipt", () -> {
            throw error;
        })).isSameAs(error);

        assertThat(fixture.timerCount("io_error")).isEqualTo(1L);
        assertThat(fixture.timerCount("timeout")).isZero();
    }

    @Test
    void classifiesCircuitOpenSeparately() {
        Fixture fixture = new Fixture();
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("stampe");
        circuitBreaker.transitionToOpenState();
        RuntimeException error = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);

        assertThatThrownBy(() -> fixture.recorder.record("stampe", "receipt", () -> {
            throw error;
        })).isSameAs(error);

        assertThat(fixture.timerCount("circuit_open")).isEqualTo(1L);
    }

    @Test
    void recordsGenericErrorOutcomeForErrors() {
        Fixture fixture = new Fixture();
        AssertionError error = new AssertionError("boom");

        assertThatThrownBy(() -> fixture.recorder.record("stampe", "receipt", () -> {
            throw error;
        })).isSameAs(error);

        assertThat(fixture.timerCount("error")).isEqualTo(1L);
    }

    private static class Fixture {
        private final ExternalCallMetricsContext context = new ExternalCallMetricsContext();
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final ExternalCallMetricsRecorder recorder =
                new ExternalCallMetricsRecorder(provider(context), registry);

        long timerCount(String outcome) {
            return ExternalCallMetricsRecorderTest.timerCount(registry, outcome);
        }
    }

    private static long timerCount(SimpleMeterRegistry registry, String outcome) {
        Timer timer = registry.find("govpay.external.service.duration")
                .tag("client", "stampe")
                .tag("operation", "receipt")
                .tag("outcome", outcome)
                .timer();
        return timer != null ? timer.count() : 0L;
    }

    private static ObjectProvider<ExternalCallMetricsContext> provider(ExternalCallMetricsContext context) {
        @SuppressWarnings("unchecked")
        ObjectProvider<ExternalCallMetricsContext> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(context);
        return provider;
    }
}
