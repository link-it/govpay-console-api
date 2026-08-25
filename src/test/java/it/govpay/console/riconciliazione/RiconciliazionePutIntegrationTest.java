package it.govpay.console.riconciliazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Fr;
import it.govpay.console.entity.GpAudit;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Incasso;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.IncassoRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Integration test di {@code PUT /riconciliazioni/{idDominio}/{id}} (74b):
 * idempotenza, 12 controlli pre-flight, ACL (servizio + dominio). I test
 * dell'hook di attivazione batch post-commit sono in
 * {@link RiconciliazionePutHookIntegrationTest} (non-transazionale: qui la
 * transazione di test viene sempre rollback-ata a fine metodo, quindi
 * {@code afterCommit()} non scatterebbe mai).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RiconciliazionePutIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM_A = "11111111111";
    private static final String DOM_B = "22222222222";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private FrRepository frRepository;
    @Autowired private IncassoRepository incassoRepository;
    @Autowired private IbanAccreditoRepository ibanAccreditoRepository;
    @Autowired private AclRepository aclRepository;
    @Autowired private GpAuditRepository gpAuditRepository;

    private Dominio domA;
    private Dominio domB;

    @BeforeEach
    void setup() {
        domA = newDominio(DOM_A, "Dominio A");
        domB = newDominio(DOM_B, "Dominio B");

        newFr(domA, "FLUSSO-PUT-OK", "ACCETTATA", false, null, 100.0);
        newFr(domA, "FLUSSO-PUT-ANOMALO", "ANOMALA", false, null, 100.0);
        newFr(domA, "FLUSSO-PUT-IMPORTO", "ACCETTATA", false, null, 999.0);

        Incasso giaRiconciliato = newIncasso(domA, "RICESISTENTE1", "ACQUISITO", "FLUSSO-PUT-RICONC", 50.0, null);
        newFr(domA, "FLUSSO-PUT-RICONC", "ACCETTATA", false, giaRiconciliato.getId(), 50.0);

        newIbanAccredito(domA, "IT60X0542811101000000123456");
    }

    // ----- nuova registrazione ------------------------------------------------------

    @Test
    void nuovaRegistrazioneConIdFlussoDirettoRitorna202() throws Exception {
        String p = utenteScrittura("u-nuova");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/NUOVA1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-OK"}"""))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/riconciliazioni/" + DOM_A + "/NUOVA1")))
                .andExpect(jsonPath("$.id", org.hamcrest.Matchers.is("NUOVA1")))
                .andExpect(jsonPath("$.stato", org.hamcrest.Matchers.is("IN_ELABORAZIONE")))
                .andExpect(jsonPath("$.idFlusso", org.hamcrest.Matchers.is("FLUSSO-PUT-OK")));

        Incasso persistito = incassoRepository.findByCodDominioAndIdentificativo(DOM_A, "NUOVA1").orElseThrow();
        assertThat(persistito.getStato()).isEqualTo("NUOVO");
        assertThat(persistito.getIuv()).isNull();
        assertThat(persistito.getTrn()).isEqualTo("FLUSSO-PUT-OK");
        assertThat(persistito.getDataOraIncasso()).isNotNull().isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    void nuovaRegistrazioneConCausaleCumulativaEstraeIdFlusso() throws Exception {
        newFr(domA, "2026-06-20-OKFLOW", "ACCETTATA", false, null, 100.0);
        String p = utenteScrittura("u-causale");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/NUOVA2")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "causale": "PUR/LGPE-RIVERSAMENTO/URI/2026-06-20-OKFLOW"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.idFlusso", org.hamcrest.Matchers.is("2026-06-20-OKFLOW")));
    }

    // ----- idempotenza -----------------------------------------------------------------

    @Test
    void reiterazioneSuStatoNonErroreRitorna200SenzaScrittura() throws Exception {
        Incasso esistente = newIncasso(domA, "RICACQ1", "ACQUISITO", "FLUSSO-PUT-STABILE", 75.0, null);
        newFr(domA, "FLUSSO-PUT-STABILE", "ACCETTATA", false, esistente.getId(), 75.0);
        OffsetDateTime dataOriginale = esistente.getDataOraIncasso();

        String p = utenteScrittura("u-idempotente");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/RICACQ1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 75.0, "idFlusso": "FLUSSO-PUT-STABILE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato", org.hamcrest.Matchers.is("ACQUISITA")));

        Incasso rilettura = incassoRepository.findByCodDominioAndIdentificativo(DOM_A, "RICACQ1").orElseThrow();
        assertThat(rilettura.getStato()).isEqualTo("ACQUISITO");
        assertThat(rilettura.getDataOraIncasso()).isEqualTo(dataOriginale);
    }

    @Test
    void reiterazioneSuStatoErroreRiaccodaERitorna202() throws Exception {
        Incasso errore = newIncasso(domA, "RICERR1", "ERRORE", "FLUSSO-PUT-ERR", 60.0, "causa precedente");
        OffsetDateTime dataErroreOriginale = errore.getDataOraIncasso();
        newFr(domA, "FLUSSO-PUT-ERR", "ACCETTATA", false, null, 60.0);

        String p = utenteScrittura("u-riaccoda");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/RICERR1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 60.0, "idFlusso": "FLUSSO-PUT-ERR"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.stato", org.hamcrest.Matchers.is("IN_ELABORAZIONE")))
                .andExpect(jsonPath("$.descrizioneStato").doesNotExist());

        Incasso rilettura = incassoRepository.findByCodDominioAndIdentificativo(DOM_A, "RICERR1").orElseThrow();
        assertThat(rilettura.getStato()).isEqualTo("NUOVO");
        assertThat(rilettura.getDescrizioneStato()).isNull();
        // E.3: la riaccoda deve rimettere la riga nella finestra di pickup del batch (data_ora_incasso aggiornata, non quella originale).
        assertThat(rilettura.getDataOraIncasso())
                .isNotEqualTo(dataErroreOriginale)
                .isAfter(dataErroreOriginale)
                .isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    void riaccodaConFlussoDiventatoAnomaloNelFrattempoRitorna409() throws Exception {
        newIncasso(domA, "RICERR2", "ERRORE", "FLUSSO-PUT-ERR-ANOM", 60.0, "causa precedente");
        // Al momento del fallimento originale il flusso era ACCETTATA; nel frattempo e' diventato anomalo.
        newFr(domA, "FLUSSO-PUT-ERR-ANOM", "ANOMALA", false, null, 60.0);

        String p = utenteScrittura("u-riaccoda-anomalo");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/RICERR2")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 60.0, "idFlusso": "FLUSSO-PUT-ERR-ANOM"}"""))
                .andExpect(status().isConflict());

        Incasso rilettura = incassoRepository.findByCodDominioAndIdentificativo(DOM_A, "RICERR2").orElseThrow();
        assertThat(rilettura.getStato()).isEqualTo("ERRORE");
    }

    @Test
    void riaccodaConFlussoGiaRiconciliatoDaAltroIncassoNelFrattempoRitorna409() throws Exception {
        newIncasso(domA, "RICERR3", "ERRORE", "FLUSSO-PUT-ERR-RIC", 60.0, "causa precedente");
        Incasso altroIncasso = newIncasso(domA, "RICALTRO1", "ACQUISITO", "FLUSSO-PUT-ERR-RIC", 60.0, null);
        // Nel frattempo un altro incasso ha riconciliato lo stesso flusso.
        newFr(domA, "FLUSSO-PUT-ERR-RIC", "ACCETTATA", false, altroIncasso.getId(), 60.0);

        String p = utenteScrittura("u-riaccoda-riconc");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/RICERR3")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 60.0, "idFlusso": "FLUSSO-PUT-ERR-RIC"}"""))
                .andExpect(status().isConflict());

        Incasso rilettura = incassoRepository.findByCodDominioAndIdentificativo(DOM_A, "RICERR3").orElseThrow();
        assertThat(rilettura.getStato()).isEqualTo("ERRORE");
    }

    @Test
    void riaccodaConImportoFlussoCambiatoNelFrattempoRitorna409() throws Exception {
        newIncasso(domA, "RICERR4", "ERRORE", "FLUSSO-PUT-ERR-IMP", 60.0, "causa precedente");
        // Al momento del fallimento originale importoTotalePagamenti era 60.0; nel frattempo e' cambiato.
        newFr(domA, "FLUSSO-PUT-ERR-IMP", "ACCETTATA", false, null, 999.0);

        String p = utenteScrittura("u-riaccoda-importo");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/RICERR4")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 60.0, "idFlusso": "FLUSSO-PUT-ERR-IMP"}"""))
                .andExpect(status().isConflict());

        Incasso rilettura = incassoRepository.findByCodDominioAndIdentificativo(DOM_A, "RICERR4").orElseThrow();
        assertThat(rilettura.getStato()).isEqualTo("ERRORE");
    }

    @Test
    void causaleEIdFlussoEntrambiPresentiIdFlussoVinceECausaleVieneSoloArchiviata() throws Exception {
        String p = utenteScrittura("u-entrambi");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/ENTRAMBI1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-OK", "causale": "testo libero non SACIV"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.idFlusso", org.hamcrest.Matchers.is("FLUSSO-PUT-OK")));

        Incasso persistito = incassoRepository.findByCodDominioAndIdentificativo(DOM_A, "ENTRAMBI1").orElseThrow();
        assertThat(persistito.getCodFlussoRendicontazione()).isEqualTo("FLUSSO-PUT-OK");
        assertThat(persistito.getCausale()).isEqualTo("testo libero non SACIV");
    }

    @Test
    void bodyConIuvRitorna400() throws Exception {
        String p = utenteScrittura("u-iuv");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/IUVBODY1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-OK", "iuv": "123456789012345"}"""))
                .andExpect(status().isBadRequest());

        assertThat(incassoRepository.findByCodDominioAndIdentificativo(DOM_A, "IUVBODY1")).isEmpty();
    }

    @Test
    void datiAccessoriDivergentiRitorna409() throws Exception {
        newIncasso(domA, "RICDIV1", "ACQUISITO", "FLUSSO-PUT-DIV", 40.0, null);
        newFr(domA, "FLUSSO-PUT-DIV", "ACCETTATA", false, null, 40.0);

        String p = utenteScrittura("u-divergente");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/RICDIV1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 999.0, "idFlusso": "FLUSSO-PUT-DIV"}"""))
                .andExpect(status().isConflict());
    }

    // ----- pre-flight --------------------------------------------------------------------

    @Test
    void bodySenzaCausaleNeIdFlussoRitorna400() throws Exception {
        String p = utenteScrittura("u-400-1");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/BAD1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void causaleNonConformeRitorna400() throws Exception {
        String p = utenteScrittura("u-400-2");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/BAD2")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "causale": "PAGAMENTO GENERICO"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void causaleConRiferimentoSingoloRitorna400() throws Exception {
        String p = utenteScrittura("u-400-3");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/BAD3")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "causale": "RFS/123456789012345"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dominioInesistenteRitorna400() throws Exception {
        String p = utenteScrittura("u-400-4");
        mvc.perform(put("/riconciliazioni/99999999999/BAD4")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-X"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ibanNonCensitoSulDominioRitorna400() throws Exception {
        String p = utenteScrittura("u-400-5");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/BAD5")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-OK", "ibanAccredito": "IT00Z0000000000000000000000"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void flussoInesistenteRitorna404() throws Exception {
        String p = utenteScrittura("u-404");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/NF1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-INESISTENTE"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void flussoGiaRiconciliatoRitorna409() throws Exception {
        String p = utenteScrittura("u-409-1");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/CONF1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 50.0, "idFlusso": "FLUSSO-PUT-RICONC"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void flussoAnomaloRitorna409() throws Exception {
        String p = utenteScrittura("u-409-2");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/CONF2")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-ANOMALO"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void importoNonCorrispondenteRitorna409() throws Exception {
        String p = utenteScrittura("u-409-3");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/CONF3")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-IMPORTO"}"""))
                .andExpect(status().isConflict());
    }

    // ----- ACL --------------------------------------------------------------------------

    @Test
    void aclScritturaNegataRitorna403() throws Exception {
        String p = utenteSoloLettura("u-403-servizio");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/ACL1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-OK"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void dominioNonVisibileInScritturaRitorna403() throws Exception {
        String p = utenteScritturaSoloDominio("u-403-dominio", domB);
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/ACL2")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-OK"}"""))
                .andExpect(status().isForbidden());
    }

    // ----- audit (successo ed eccezioni) -----------------------------------------------

    @Test
    void auditRegistratoConEsito202SuNuovaRegistrazione() throws Exception {
        String p = utenteScrittura("u-audit-202");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/AUDIT1")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-OK"}"""))
                .andExpect(status().isAccepted());

        GpAudit riga = ultimoAudit("AUDIT1");
        assertThat(riga.getOggetto()).contains("\"esito\":\"202\"").contains("\"idFlusso\":\"FLUSSO-PUT-OK\"");
    }

    @Test
    void auditRegistratoConEsito200SuIdempotente() throws Exception {
        newIncasso(domA, "AUDIT2", "ACQUISITO", "FLUSSO-PUT-STABILE2", 80.0, null);
        newFr(domA, "FLUSSO-PUT-STABILE2", "ACCETTATA", false, null, 80.0);

        String p = utenteScrittura("u-audit-200");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/AUDIT2")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 80.0, "idFlusso": "FLUSSO-PUT-STABILE2"}"""))
                .andExpect(status().isOk());

        GpAudit riga = ultimoAudit("AUDIT2");
        assertThat(riga.getOggetto()).contains("\"esito\":\"200\"");
    }

    @Test
    void auditRegistratoConEsito400SuPreFlightFallito() throws Exception {
        String p = utenteScrittura("u-audit-400");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/AUDIT3")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "causale": "PAGAMENTO GENERICO"}"""))
                .andExpect(status().isBadRequest());

        GpAudit riga = ultimoAudit("AUDIT3");
        assertThat(riga.getOggetto()).contains("\"esito\":\"400\"");
        assertThat(riga.getIdOggetto()).isEqualTo(0L);
    }

    @Test
    void auditRegistratoConEsito409SuConflitto() throws Exception {
        String p = utenteScrittura("u-audit-409");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/AUDIT4")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 50.0, "idFlusso": "FLUSSO-PUT-RICONC"}"""))
                .andExpect(status().isConflict());

        GpAudit riga = ultimoAudit("AUDIT4");
        assertThat(riga.getOggetto()).contains("\"esito\":\"409\"");
    }

    @Test
    void auditRegistratoConEsito403SuAclServizioNegata() throws Exception {
        String p = utenteSoloLettura("u-audit-403");
        mvc.perform(put("/riconciliazioni/" + DOM_A + "/AUDIT5")
                        .with(httpBasic(p, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"importo": 100.0, "idFlusso": "FLUSSO-PUT-OK"}"""))
                .andExpect(status().isForbidden());

        GpAudit riga = ultimoAudit("AUDIT5");
        assertThat(riga.getOggetto()).contains("\"esito\":\"403\"");
    }

    private GpAudit ultimoAudit(String id) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> "RICONCILIAZIONE_REGISTRA".equals(a.getTipoOggetto())
                        && a.getOggetto() != null && a.getOggetto().contains("\"id\":\"" + id + "\""))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("Nessuna riga di audit trovata per id=" + id));
    }

    // ----- fixture helpers -----------------------------------------------------------

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private Fr newFr(Dominio dominio, String codFlusso, String stato, boolean obsoleto, Long idIncasso, Double importo) {
        Fr fr = new Fr();
        fr.setIdDominio(dominio.getId());
        fr.setCodDominio(dominio.getCodDominio());
        fr.setCodFlusso(codFlusso);
        fr.setCodPsp("PSP-1");
        fr.setRevisione(1L);
        fr.setStato(stato);
        fr.setIur("TRN-" + codFlusso);
        fr.setDataOraFlusso(date(2026, 6, 20));
        fr.setDataAcquisizione(date(2026, 6, 20));
        fr.setNumeroPagamenti(1L);
        fr.setImportoTotalePagamenti(importo);
        fr.setObsoleto(obsoleto);
        fr.setIdIncasso(idIncasso);
        return frRepository.save(fr);
    }

    private Incasso newIncasso(Dominio dominio, String identificativo, String stato, String idFlusso,
                               Double importo, String descrizioneStato) {
        Incasso i = new Incasso();
        i.setIdentificativo(identificativo);
        i.setCodDominio(dominio.getCodDominio());
        i.setTrn(idFlusso);
        i.setImporto(importo);
        i.setDataOraIncasso(date(2026, 6, 15));
        i.setCodFlussoRendicontazione(idFlusso);
        i.setStato(stato);
        i.setDescrizioneStato(descrizioneStato);
        return incassoRepository.save(i);
    }

    private IbanAccredito newIbanAccredito(Dominio dominio, String codIban) {
        IbanAccredito iban = new IbanAccredito();
        iban.setDominio(dominio);
        iban.setCodIban(codIban);
        iban.setAbilitato(true);
        iban.setPostale(false);
        return ibanAccreditoRepository.save(iban);
    }

    private static OffsetDateTime date(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private String utenteScrittura(String principal) {
        Utenza u = baseUtenza(principal, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grant(u, "RW");
        return principal;
    }

    private String utenteScritturaSoloDominio(String principal, Dominio dominio) {
        Utenza u = baseUtenza(principal, false);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grant(u, "RW");
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(dominio.getId());
        utenzaDominioRepository.save(link);
        return principal;
    }

    private String utenteSoloLettura(String principal) {
        Utenza u = baseUtenza(principal, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grant(u, "R");
        return principal;
    }

    private void grant(Utenza utenza, String diritti) {
        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio(AclServizio.RENDICONTAZIONI_E_INCASSI.getValue());
        acl.setDiritti(diritti);
        aclRepository.save(acl);
    }

    private Utenza baseUtenza(String principal, boolean tuttiDomini) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(tuttiDomini);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        return u;
    }

    private void attachOperatore(String nome, Utenza u) {
        Operatore op = new Operatore();
        op.setNome(nome);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);
    }
}
