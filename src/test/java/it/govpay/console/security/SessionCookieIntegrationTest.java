package it.govpay.console.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Attributi dei cookie di sessione su un server HTTP reale (issue #128): MockMvc
 * non applica la configurazione {@code server.servlet.session.cookie.*} del
 * container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionCookieIntegrationTest {

    private static final String PRINCIPAL = "cookie-samesite";
    private static final String PASSWORD = "secret";

    @LocalServerPort
    private int port;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setupUtenza() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setPassword(encoder.encode(PASSWORD));
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(true);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("AMMINISTRATORE");
        utenza = utenzaRepository.save(utenza);

        Operatore operatore = new Operatore();
        operatore.setIdUtenza(utenza.getId());
        operatore.setNome("Cookie SameSite");
        operatoreRepository.save(operatore);
    }

    @AfterEach
    void cleanup() {
        utenzaRepository.findByPrincipal(PRINCIPAL).ifPresent(u -> {
            operatoreRepository.findAll().stream()
                    .filter(o -> u.getId().equals(o.getIdUtenza()))
                    .forEach(operatoreRepository::delete);
            utenzaRepository.delete(u);
        });
    }

    @Test
    void loginImpostaSameSiteSulCookieDiSessione() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + PRINCIPAL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> cookies = response.headers().allValues("Set-Cookie");
        assertThat(cookie(cookies, "JSESSIONID")).containsIgnoringCase("SameSite=Lax").containsIgnoringCase("HttpOnly");
        assertThat(cookie(cookies, "XSRF-TOKEN")).containsIgnoringCase("SameSite=Lax");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String cookie(List<String> setCookies, String name) {
        return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError("Cookie " + name + " assente in " + setCookies));
    }
}
