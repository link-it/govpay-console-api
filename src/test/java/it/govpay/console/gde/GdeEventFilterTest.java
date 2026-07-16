package it.govpay.console.gde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import it.govpay.common.gde.GdeEventInfo;
import it.govpay.gde.client.beans.EsitoEvento;
import jakarta.servlet.FilterChain;

class GdeEventFilterTest {

    private final ConsoleGdeService consoleGdeService = mock(ConsoleGdeService.class);
    private final GdeEventFilter filter = new GdeEventFilter(consoleGdeService);

    @Test
    void unhandledExceptionWithResponseStillOkIsClassifiedAsFailWith500() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pendenze");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // di default MockHttpServletResponse resta a 200: nessun exception handler
        // ha ancora avuto modo di impostare uno status di errore.
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        GdeEventInfo eventInfo = capturedEventInfo();
        assertThat(eventInfo.getStatusCodeRisposta()).isEqualTo(500);
        assertThat(eventInfo.getEsito()).isEqualTo(EsitoEvento.FAIL);
        assertThat(eventInfo.getDescrizioneEsito()).isEqualTo("boom");
    }

    @Test
    void successfulRequestIsClassifiedAsOk() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pendenze");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/pendenze");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        GdeEventInfo eventInfo = capturedEventInfo();
        assertThat(eventInfo.getStatusCodeRisposta()).isEqualTo(200);
        assertThat(eventInfo.getEsito()).isEqualTo(EsitoEvento.OK);
        assertThat(eventInfo.getDescrizioneEsito()).isNull();
    }

    @Test
    void principalIsReadFromInterceptorAttributeNotSecurityContextHolder() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pendenze");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // SecurityContextHolder e' vuoto in questo test: il principal arriva
        // solo dall'attributo che OperationIdHandlerInterceptor valorizzerebbe
        // a runtime (qui simulato direttamente, prima che la chain esegua).
        FilterChain chain = (req, res) ->
                req.setAttribute(OperationIdHandlerInterceptor.PRINCIPAL_ATTRIBUTE, "gpadmin");

        filter.doFilter(request, response, chain);

        GdeEventInfo eventInfo = capturedEventInfo();
        assertThat(eventInfo.getPrincipal()).isEqualTo("gpadmin");
    }

    @Test
    void principalIsNullWhenAttributeAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/methods");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        GdeEventInfo eventInfo = capturedEventInfo();
        assertThat(eventInfo.getPrincipal()).isNull();
    }

    @Test
    void handledClientErrorWithoutExceptionIsClassifiedAsKoNot500() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pendenze/999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(404);

        filter.doFilter(request, response, chain);

        GdeEventInfo eventInfo = capturedEventInfo();
        assertThat(eventInfo.getStatusCodeRisposta()).isEqualTo(404);
        assertThat(eventInfo.getEsito()).isEqualTo(EsitoEvento.KO);
    }

    private GdeEventInfo capturedEventInfo() {
        ArgumentCaptor<GdeEventInfo> captor = ArgumentCaptor.forClass(GdeEventInfo.class);
        verify(consoleGdeService).inviaEventoAsync(captor.capture());
        return captor.getValue();
    }
}
