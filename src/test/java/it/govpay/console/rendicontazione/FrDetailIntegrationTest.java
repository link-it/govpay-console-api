package it.govpay.console.rendicontazione;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Fr;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Rendicontazione;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.RendicontazioneRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Integration test del dettaglio {@code GET /flussi-rendicontazione/{idDominio}/{idFlusso}/{idPsp}/{revisione}}
 * (issue #50, PR 70b): content negotiation JSON/XML, ACL, disambiguazione via
 * quaterna, dataInizio/dataFine calcolate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FrDetailIntegrationTest {

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
    @Autowired private RendicontazioneRepository rendicontazioneRepository;
    @PersistenceContext private EntityManager entityManager;

    private Dominio domA;
    private Dominio domB;
    private Fr flussoConXml;

    @BeforeEach
    void setup() {
        domA = newDominio(DOM_A, "Dominio A");
        domB = newDominio(DOM_B, "Dominio B");

        flussoConXml = newFr(domA, "FLUSSO-X", "PSP-1", 1L, "<FlussoRiversamento/>".getBytes());
        newFr(domA, "FLUSSO-Y", "PSP-1", 1L, null);
        newFr(domB, "FLUSSO-REV", "PSP-2", 1L, "<v1/>".getBytes());
        newFr(domB, "FLUSSO-REV", "PSP-2", 2L, "<v2/>".getBytes());

        // 2 rendicontazioni per flussoConXml: finestra temporale 2026-06-10..2026-06-15.
        newRendicontazione(flussoConXml.getId(), "IUV-1", date(2026, 6, 10));
        newRendicontazione(flussoConXml.getId(), "IUV-2", date(2026, 6, 15));
    }

    @Test
    void dettaglioJsonConDataInizioFine() throws Exception {
        String p = utenteDominiStar("u-json");
        mvc.perform(get("/flussi-rendicontazione/" + DOM_A + "/FLUSSO-X/PSP-1/1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDominio", is(DOM_A)))
                .andExpect(jsonPath("$.idFlusso", is("FLUSSO-X")))
                .andExpect(jsonPath("$.idPsp", is("PSP-1")))
                .andExpect(jsonPath("$.revisione", is(1)))
                .andExpect(jsonPath("$.dataInizio").exists())
                .andExpect(jsonPath("$.dataFine").exists())
                .andExpect(jsonPath("$._links.dominio.href", is("/domini/" + DOM_A)));
    }

    @Test
    void dettaglioJsonSenzaRendicontazioniDataInizioFineAssenti() throws Exception {
        String p = utenteDominiStar("u-noperiodo");
        mvc.perform(get("/flussi-rendicontazione/" + DOM_A + "/FLUSSO-Y/PSP-1/1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataInizio").doesNotExist())
                .andExpect(jsonPath("$.dataFine").doesNotExist());
    }

    @Test
    void dettaglioXmlOriginale() throws Exception {
        String p = utenteDominiStar("u-xml");
        mvc.perform(get("/flussi-rendicontazione/" + DOM_A + "/FLUSSO-X/PSP-1/1")
                        .with(httpBasic(p, PASSWORD))
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"flusso-FLUSSO-X-r1.xml\""))
                .andExpect(content().string("<FlussoRiversamento/>"));
    }

    @Test
    void dettaglioXmlNonArchiviatoRitorna404() throws Exception {
        String p = utenteDominiStar("u-noxml");
        mvc.perform(get("/flussi-rendicontazione/" + DOM_A + "/FLUSSO-Y/PSP-1/1")
                        .with(httpBasic(p, PASSWORD))
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptNonSupportatoRitorna406() throws Exception {
        String p = utenteDominiStar("u-406");
        mvc.perform(get("/flussi-rendicontazione/" + DOM_A + "/FLUSSO-X/PSP-1/1")
                        .with(httpBasic(p, PASSWORD))
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void quaternaSconosciutaRitorna404() throws Exception {
        String p = utenteDominiStar("u-404");
        mvc.perform(get("/flussi-rendicontazione/" + DOM_A + "/NOPE/PSP-1/1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aclNegataRitorna404AntiLeak() throws Exception {
        String p = utenteSoloDominio("u-acl404", domB);
        mvc.perform(get("/flussi-rendicontazione/" + DOM_A + "/FLUSSO-X/PSP-1/1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void revisioniMultipleDisambiguateDallaQuaterna() throws Exception {
        String p = utenteDominiStar("u-rev");
        mvc.perform(get("/flussi-rendicontazione/" + DOM_B + "/FLUSSO-REV/PSP-2/1").with(httpBasic(p, PASSWORD))
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(content().string("<v1/>"));
        mvc.perform(get("/flussi-rendicontazione/" + DOM_B + "/FLUSSO-REV/PSP-2/2").with(httpBasic(p, PASSWORD))
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(content().string("<v2/>"));
    }

    // ----- fixture helpers -----------------------------------------------------------

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private Fr newFr(Dominio dominio, String codFlusso, String codPsp, Long revisione, byte[] xml) {
        Fr fr = new Fr();
        fr.setIdDominio(dominio.getId());
        fr.setCodDominio(dominio.getCodDominio());
        fr.setCodFlusso(codFlusso);
        fr.setCodPsp(codPsp);
        fr.setRevisione(revisione);
        fr.setStato("ACCETTATA");
        fr.setIur("TRN-" + codFlusso + "-" + revisione);
        fr.setDataOraFlusso(date(2026, 6, 20));
        fr.setDataRegolamento(date(2026, 6, 21));
        fr.setDataAcquisizione(date(2026, 6, 20));
        fr.setNumeroPagamenti(2L);
        fr.setImportoTotalePagamenti(30.0);
        fr.setObsoleto(false);
        fr = frRepository.saveAndFlush(fr);

        if (xml != null) {
            // xml e' colonna della stessa tabella 'fr', letta in produzione tramite
            // la proiezione FrXml. Qui va scritta con un UPDATE bulk (non
            // FrXmlRepository.save()): essendo un'altra entity JPA sulla stessa
            // tabella, save() farebbe un merge() che non vede l'insert pendente di
            // Fr (nessun auto-flush tra entity diverse sulla stessa tabella fisica)
            // e tenterebbe un INSERT duplicato, fallendo sul NOT NULL di 'obsoleto'
            // (colonna che FrXml non mappa nemmeno).
            entityManager.createQuery("update FrXml f set f.xml = :xml where f.id = :id")
                    .setParameter("xml", xml)
                    .setParameter("id", fr.getId())
                    .executeUpdate();
        }
        return fr;
    }

    private void newRendicontazione(Long idFr, String iuv, OffsetDateTime data) {
        Rendicontazione r = new Rendicontazione();
        r.setIdFr(idFr);
        r.setIuv(iuv);
        r.setData(data);
        rendicontazioneRepository.save(r);
    }

    private static OffsetDateTime date(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private String utenteDominiStar(String principal) {
        Utenza u = baseUtenza(principal, true, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        return principal;
    }

    private String utenteSoloDominio(String principal, Dominio dominio) {
        Utenza u = baseUtenza(principal, false, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(dominio.getId());
        utenzaDominioRepository.save(link);
        return principal;
    }

    private Utenza baseUtenza(String principal, boolean tuttiDomini, boolean tuttiTipi) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(tuttiDomini);
        u.setAutorizzazioneTipiVersStar(tuttiTipi);
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
