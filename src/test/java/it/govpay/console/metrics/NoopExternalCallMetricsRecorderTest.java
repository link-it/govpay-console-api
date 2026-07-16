package it.govpay.console.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NoopExternalCallMetricsRecorderTest {

    @Test
    void recordExecutesTheCallWithoutAnyRegistry() {
        NoopExternalCallMetricsRecorder recorder = new NoopExternalCallMetricsRecorder();
        boolean[] called = { false };

        recorder.record("stampe", "payment_notice", () -> called[0] = true);

        assertThat(called[0]).isTrue();
    }

    @Test
    void recordPropagatesExceptionsFromTheCall() {
        NoopExternalCallMetricsRecorder recorder = new NoopExternalCallMetricsRecorder();
        RuntimeException error = new RuntimeException("boom");

        assertThatThrownBy(() -> recorder.record("stampe", "payment_notice", () -> {
            throw error;
        })).isSameAs(error);
    }
}
