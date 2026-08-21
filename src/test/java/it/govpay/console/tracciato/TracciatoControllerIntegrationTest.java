package it.govpay.console.tracciato;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TracciatoControllerIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String BASE = "/pendenze/tracciati";
    private static final String SERVIZIO_PENDENZE = "Pendenze";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private AclRepository aclRepository;
    @Autowired
    private DominioRepository dominioRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    private Dominio dominio;

    @BeforeEach
    void setup() {
        dominio = new Dominio();
        dominio.setCodDominio("12345678901");
        dominio.setRagioneSociale("Comune Fixture Tracciati");
        dominio.setAuxDigit(0);
        dominioRepository.save(dominio);
    }

    private String utenzaConDominioEScrittura(String principal) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(false);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setNome(principal);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);

        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(dominio.getId());
        utenzaDominioRepository.save(link);

        Acl acl = new Acl();
        acl.setIdUtenza(u.getId());
        acl.setServizio(SERVIZIO_PENDENZE);
        acl.setDiritti("RW");
        aclRepository.save(acl);

        return principal;
    }

    private String utenzaSenzaAclScrittura(String principal) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(true);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setNome(principal);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);
        return principal;
    }

    private static String jsonBody(String idDominioRoot, String idDominioRiga) {
        return """
                {
                  "idDominio": "%s",
                  "inserimenti": [
                    {
                      "idDominio": "%s",
                      "importo": 10.0,
                      "causale": "Causale di test",
                      "idA2A": "A2A_TEST",
                      "idPendenza": "PEND-001",
                      "voci": [
                        {"idVocePendenza": "V1", "importo": 10.0, "descrizione": "Voce di test", "stato": "NON_PAGATA", "codEntrata": "TARI"}
                      ]
                    }
                  ]
                }
                """.formatted(idDominioRoot, idDominioRiga);
    }

    // ----- upload -----------------------------------------------------------

    @Test
    void uploadJsonValidoReturns202EScriveAudit() throws Exception {
        String principal = utenzaConDominioEScrittura("op-upload-json");
        long auditPrima = gpAuditRepository.count();

        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("12345678901", "12345678901")))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.stato", is("IN_ATTESA")))
                .andExpect(jsonPath("$.formatoRichiesta", is("JSON")))
                .andExpect(jsonPath("$.dominio.idDominio", is("12345678901")));

        org.assertj.core.api.Assertions.assertThat(gpAuditRepository.count()).isEqualTo(auditPrima + 1);
    }

    @Test
    void uploadSenzaAclScritturaReturns403() throws Exception {
        String principal = utenzaSenzaAclScrittura("op-no-acl");
        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("12345678901", "12345678901")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void uploadDominioSconosciutoReturns422() throws Exception {
        String principal = utenzaConDominioEScrittura("op-dominio-sconosciuto");
        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("99999999999", "99999999999")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void uploadDominioNonAutorizzatoReturns403() throws Exception {
        Dominio altro = new Dominio();
        altro.setCodDominio("00000000001");
        altro.setRagioneSociale("Altro Dominio");
        altro.setAuxDigit(0);
        dominioRepository.save(altro);

        String principal = utenzaConDominioEScrittura("op-altro-dominio");
        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("00000000001", "00000000001")))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadJsonMalformatoReturns400() throws Exception {
        String principal = utenzaConDominioEScrittura("op-json-malformato");
        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ non e' json valido"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadJsonRigaConDominioDiversoReturns400() throws Exception {
        String principal = utenzaConDominioEScrittura("op-multi-dominio");
        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("12345678901", "00000000001")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadCsvSenzaIdDominioReturns400() throws Exception {
        String principal = utenzaConDominioEScrittura("op-csv-no-dominio");
        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType("text/csv")
                        .content("idA2A;idPendenza;importo\nA2A;P1;10.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadCsvConIdDominioReturns202() throws Exception {
        String principal = utenzaConDominioEScrittura("op-csv-ok");
        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType("text/csv")
                        .param("idDominio", "12345678901")
                        .content("idA2A;idPendenza;importo\nA2A;P1;10.0"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.formatoRichiesta", is("CSV")));
    }

    @Test
    void uploadContentTypeNonSupportatoReturns415() throws Exception {
        String principal = utenzaConDominioEScrittura("op-content-type-ko");
        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<xml/>"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadMultipartJsonReturns202() throws Exception {
        String principal = utenzaConDominioEScrittura("op-multipart");
        MockMultipartFile file = new MockMultipartFile("file", "tracciato.json", "application/json",
                jsonBody("12345678901", "12345678901").getBytes());
        mvc.perform(multipart(BASE).file(file).with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.formatoRichiesta", is("JSON")));
    }

    // ----- lista --------------------------------------------------------------

    @Test
    void listaFiltraPerIdDominioEStato() throws Exception {
        String principal = utenzaConDominioEScrittura("op-lista");
        mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("12345678901", "12345678901")))
                .andExpect(status().isAccepted());

        mvc.perform(get(BASE).with(httpBasic(principal, PASSWORD))
                        .param("idDominio", "12345678901")
                        .param("stato", "IN_ATTESA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)));

        mvc.perform(get(BASE).with(httpBasic(principal, PASSWORD))
                        .param("idDominio", "00000000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void listaCursorPagination() throws Exception {
        String principal = utenzaConDominioEScrittura("op-cursor");
        for (int i = 0; i < 3; i++) {
            mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBody("12345678901", "12345678901")))
                    .andExpect(status().isAccepted());
        }

        mvc.perform(get(BASE).with(httpBasic(principal, PASSWORD)).param("cursor", "").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.nextCursor").exists());
    }

    @Test
    void listaNonVedeTracciatiDiAltroDominio() throws Exception {
        Dominio altro = new Dominio();
        altro.setCodDominio("00000000002");
        altro.setRagioneSociale("Altro Dominio 2");
        altro.setAuxDigit(0);
        dominioRepository.save(altro);

        String uploader = utenzaConDominioEScrittura("op-owner");
        mvc.perform(post(BASE).with(httpBasic(uploader, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("12345678901", "12345678901")))
                .andExpect(status().isAccepted());

        // Utenza con visibilita' solo sull'altro dominio: non deve vedere il tracciato caricato sopra.
        Utenza u = new Utenza();
        u.setPrincipal("op-other-visibility");
        u.setPrincipalOriginale("op-other-visibility");
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(false);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);
        Operatore op = new Operatore();
        op.setNome("op-other-visibility");
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(altro.getId());
        utenzaDominioRepository.save(link);

        mvc.perform(get(BASE).with(httpBasic("op-other-visibility", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));
    }

    // ----- dettaglio ------------------------------------------------------------

    @Test
    void dettaglioTracciatoEsistenteReturns200() throws Exception {
        String principal = utenzaConDominioEScrittura("op-dettaglio");
        String location = mvc.perform(post(BASE).with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("12345678901", "12345678901")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location).with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, max-age=60"))
                .andExpect(jsonPath("$.stato", is("IN_ATTESA")));
    }

    @Test
    void dettaglioTracciatoInesistenteReturns404() throws Exception {
        String principal = utenzaConDominioEScrittura("op-dettaglio-404");
        mvc.perform(get(BASE + "/999999999").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isNotFound());
    }
}
