package it.govpay.console.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(TestProblemController.class)
class ProblemExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationFailureReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/_test-problem/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.instance", is("/_test-problem/validation")))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name")));
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        mockMvc.perform(post("/_test-problem/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void notFoundExceptionReturns404() throws Exception {
        mockMvc.perform(get("/_test-problem/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.title", is("Not Found")))
                .andExpect(jsonPath("$.instance", is("/_test-problem/not-found")));
    }

    @Test
    void missingHandlerReturns404Problem() throws Exception {
        mockMvc.perform(get("/path-inesistente"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void ifMatchMismatchReturns412() throws Exception {
        mockMvc.perform(get("/_test-problem/optimistic-lock"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(412)))
                .andExpect(jsonPath("$.title", is("Precondition Failed")));
    }

    @Test
    void dataIntegrityViolationReturns409() throws Exception {
        mockMvc.perform(get("/_test-problem/integrity"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.title", is("Conflict")));
    }

    @Test
    void genericExceptionReturns500WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/_test-problem/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.title", is("Internal Server Error")))
                .andExpect(jsonPath("$.detail", is("Errore interno del server.")))
                .andExpect(jsonPath("$.detail", not("dettaglio interno che non deve uscire")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("IllegalStateException"))));
    }
}
