package it.govpay.console.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesIdWhenNoHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDCCapturingChain chain = new MDCCapturingChain();

        filter.doFilter(request, response, chain);

        String legacyOut = response.getHeader(RequestIdFilter.LEGACY_HEADER);
        String standardOut = response.getHeader(RequestIdFilter.REQUEST_HEADER);
        assertThat(legacyOut).isNotBlank();
        assertThat(UUID.fromString(legacyOut)).isNotNull();
        assertThat(standardOut).isEqualTo(legacyOut);
        assertThat(chain.capturedMdcValue).isEqualTo(legacyOut);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void reusesIdFromStandardHeader() throws Exception {
        String incomingId = "standard-incoming-id";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_HEADER, incomingId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDCCapturingChain chain = new MDCCapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.LEGACY_HEADER)).isEqualTo(incomingId);
        assertThat(response.getHeader(RequestIdFilter.REQUEST_HEADER)).isEqualTo(incomingId);
        assertThat(chain.capturedMdcValue).isEqualTo(incomingId);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void reusesIdFromLegacyHeader() throws Exception {
        String incomingId = "legacy-incoming-id";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.LEGACY_HEADER, incomingId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDCCapturingChain chain = new MDCCapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.LEGACY_HEADER)).isEqualTo(incomingId);
        assertThat(response.getHeader(RequestIdFilter.REQUEST_HEADER)).isEqualTo(incomingId);
        assertThat(chain.capturedMdcValue).isEqualTo(incomingId);
    }

    @Test
    void standardHeaderTakesPrecedenceOverLegacy() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_HEADER, "from-standard");
        request.addHeader(RequestIdFilter.LEGACY_HEADER, "from-legacy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDCCapturingChain chain = new MDCCapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.LEGACY_HEADER)).isEqualTo("from-standard");
        assertThat(response.getHeader(RequestIdFilter.REQUEST_HEADER)).isEqualTo("from-standard");
        assertThat(chain.capturedMdcValue).isEqualTo("from-standard");
    }

    @Test
    void generatesIdWhenBothHeadersAreBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_HEADER, "  ");
        request.addHeader(RequestIdFilter.LEGACY_HEADER, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDCCapturingChain chain = new MDCCapturingChain();

        filter.doFilter(request, response, chain);

        String legacyOut = response.getHeader(RequestIdFilter.LEGACY_HEADER);
        String standardOut = response.getHeader(RequestIdFilter.REQUEST_HEADER);
        assertThat(legacyOut).isNotBlank();
        assertThat(UUID.fromString(legacyOut)).isNotNull();
        assertThat(standardOut).isEqualTo(legacyOut);
    }

    private static class MDCCapturingChain extends MockFilterChain {
        String capturedMdcValue;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            this.capturedMdcValue = MDC.get(RequestIdFilter.MDC_KEY);
        }
    }
}
