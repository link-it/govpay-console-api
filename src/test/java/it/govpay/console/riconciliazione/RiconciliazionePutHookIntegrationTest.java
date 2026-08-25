package it.govpay.console.riconciliazione;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Fr;
import it.govpay.console.entity.Incasso;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.AclServizio;
import it.govpay.console.operazioni.OperazioneBatchClient;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.repository.IncassoRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Test dedicato all'hook di attivazione batch post-commit di
 * {@code PUT /riconciliazioni/{idDominio}/{id}}. Volutamente **non**
 * {@code @Transactional}: la transazione del PUT deve committare davvero
 * perché {@code TransactionSynchronization#afterCommit()} scatti (con
 * {@code @Transactional} a livello di classe l'intero test viene rollback-ato
 * a fine metodo e {@code afterCommit()} non parte mai). Cleanup manuale in
 * {@code @AfterEach}, id univoci per metodo per evitare collisioni.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiconciliazionePutHookIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM_HOOK = "33333333333";
    private static final String ID_OPERAZIONE_TRIGGER = "ELABORAZIONE_RICONCILIAZIONI";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private FrRepository frRepository;
    @Autowired private IncassoRepository incassoRepository;
    @Autowired private AclRepository aclRepository;
    @Autowired private OperazioniProperties operazioniProperties;

    @MockitoBean private OperazioneBatchClient operazioneBatchClient;

    private Dominio dominio;

    @BeforeEach
    void setup() {
        dominio = newDominio(DOM_HOOK, "Dominio Hook");
    }

    @AfterEach
    void cleanup() {
        operazioniProperties.setCatalogo(new ArrayList<>());
        incassoRepository.findByCodDominioAndIdentificativo(DOM_HOOK, "HOOK1").ifPresent(incassoRepository::delete);
        incassoRepository.findByCodDominioAndIdentificativo(DOM_HOOK, "HOOK2").ifPresent(incassoRepository::delete);
        incassoRepository.findByCodDominioAndIdentificativo(DOM_HOOK, "HOOK3").ifPresent(incassoRepository::delete);
        incassoRepository.findByCodDominioAndIdentificativo(DOM_HOOK, "HOOK4").ifPresent(incassoRepository::delete);
        frRepository.findByCodDominioAndCodFlussoAndObsoletoFalse(DOM_HOOK, "FLUSSO-HOOK1")
                .ifPresent(frRepository::delete);
        frRepository.findByCodDominioAndCodFlussoAndObsoletoFalse(DOM_HOOK, "FLUSSO-HOOK2")
                .ifPresent(frRepository::delete);
        frRepository.findByCodDominioAndCodFlussoAndObsoletoFalse(DOM_HOOK, "FLUSSO-HOOK3")
                .ifPresent(frRepository::delete);
        frRepository.findByCodDominioAndCodFlussoAndObsoletoFalse(DOM_HOOK, "FLUSSO-HOOK4")
                .ifPresent(frRepository::delete);
        dominioRepository.delete(dominio);
    }

    @Test
    void hookNonCensitoNonInvocaClient() throws Exception {
        newFr("FLUSSO-HOOK1", 100.0);
        String p = utenteScrittura("u-hook-noop");
        mvc.perform(put("/riconciliazioni/" + DOM_HOOK + "/HOOK1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-HOOK1"}"""))
                .andExpect(status().isAccepted());
        verifyNoInteractions(operazioneBatchClient);
    }

    @Test
    void hookCensitoInvocaClientDopoCommit() throws Exception {
        newFr("FLUSSO-HOOK2", 100.0);
        censisciOperazione("http://iban-batch:8080/api/batch", true);
        String p = utenteScrittura("u-hook-trigger");
        mvc.perform(put("/riconciliazioni/" + DOM_HOOK + "/HOOK2")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-HOOK2"}"""))
                .andExpect(status().isAccepted());
        verify(operazioneBatchClient).run("http://iban-batch:8080/api/batch", false);
    }

    @Test
    void hookNonAbilitataNonInvocaClient() throws Exception {
        newFr("FLUSSO-HOOK3", 100.0);
        censisciOperazione("http://iban-batch:8080/api/batch", false);
        String p = utenteScrittura("u-hook-disabilitata");
        mvc.perform(put("/riconciliazioni/" + DOM_HOOK + "/HOOK3")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-HOOK3"}"""))
                .andExpect(status().isAccepted());
        verifyNoInteractions(operazioneBatchClient);
    }

    @Test
    void hookFallitoNonBloccaRisposta() throws Exception {
        newFr("FLUSSO-HOOK4", 100.0);
        censisciOperazione("http://iban-batch:8080/api/batch", true);
        doThrow(new RuntimeException("batch irraggiungibile"))
                .when(operazioneBatchClient).run(anyString(), anyBoolean());

        String p = utenteScrittura("u-hook-fallito");
        mvc.perform(put("/riconciliazioni/" + DOM_HOOK + "/HOOK4")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-HOOK4"}"""))
                .andExpect(status().isAccepted());
        verify(operazioneBatchClient).run("http://iban-batch:8080/api/batch", false);
    }

    // ----- fixture helpers -----------------------------------------------------------

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private void newFr(String codFlusso, Double importo) {
        Fr fr = new Fr();
        fr.setIdDominio(dominio.getId());
        fr.setCodDominio(dominio.getCodDominio());
        fr.setCodFlusso(codFlusso);
        fr.setCodPsp("PSP-1");
        fr.setRevisione(1L);
        fr.setStato("ACCETTATA");
        fr.setIur("TRN-" + codFlusso);
        fr.setDataOraFlusso(date(2026, 6, 20));
        fr.setDataAcquisizione(date(2026, 6, 20));
        fr.setNumeroPagamenti(1L);
        fr.setImportoTotalePagamenti(importo);
        fr.setObsoleto(false);
        frRepository.save(fr);
    }

    private void censisciOperazione(String url, boolean abilitata) {
        OperazioneConfig config = new OperazioneConfig();
        config.setId(ID_OPERAZIONE_TRIGGER);
        config.setUrl(url);
        config.setAbilitata(abilitata);
        operazioniProperties.setCatalogo(List.of(config));
    }

    private static OffsetDateTime date(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private String utenteScrittura(String principal) {
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

        Acl acl = new Acl();
        acl.setIdUtenza(u.getId());
        acl.setServizio(AclServizio.RENDICONTAZIONI_E_INCASSI.getValue());
        acl.setDiritti("RW");
        aclRepository.save(acl);

        return principal;
    }
}
