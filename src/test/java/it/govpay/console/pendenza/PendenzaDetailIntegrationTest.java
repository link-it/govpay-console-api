package it.govpay.console.pendenza;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.TipoTributo;
import it.govpay.console.entity.Tributo;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.Versamento;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.SingoloVersamentoRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PendenzaDetailIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String APP_COD = "APP-A";

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
    private DominioRepository dominioRepository;
    @Autowired
    private ApplicazioneRepository applicazioneRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired
    private VersamentoRepository versamentoRepository;
    @Autowired
    private SingoloVersamentoRepository singoloVersamentoRepository;
    @Autowired
    private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setup() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(false);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("OPERATORE");
        utenza.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(utenza);

        Operatore op = new Operatore();
        op.setNome("Operatore Uno");
        op.setIdUtenza(utenza.getId());
        operatoreRepository.save(op);

        Dominio domVisibile = saveDominio("11111111111", "Dominio Visibile");
        Dominio domNonVisibile = saveDominio("22222222222", "Dominio NON Visibile");

        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(utenza.getId());
        link.setIdDominio(domVisibile.getId());
        utenzaDominioRepository.save(link);

        Applicazione app = new Applicazione();
        app.setCodApplicazione(APP_COD);
        applicazioneRepository.save(app);

        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("Tassa rifiuti");
        tipoVersamentoRepository.save(tv);

        TipoVersamentoDominio tvdVis = saveTvd(domVisibile, tv);
        TipoVersamentoDominio tvdNon = saveTvd(domNonVisibile, tv);

        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo("UO1");
        uo.setUoDenominazione("Ufficio Tributi");
        uo.setDominio(domVisibile);
        unitaOperativaRepository.save(uo);

        // 1) Pendenza completa con voci, avviso e dati allegati / proprieta
        Versamento pendenzaCompleta = newPendenza(
                "PEND-FULL", domVisibile, app, tv, tvdVis, uo,
                "RSSMRA80A01H501U", "001234567890123456",
                100.0, "NON_ESEGUITO", 1,
                "{\"key\":\"valore-allegato\"}",
                "{\"prop\":\"valore-prop\"}");
        addVoce(pendenzaCompleta, "VOCE-1", 1, 40.0, "Quota A", "NON_ESEGUITO");
        addVoce(pendenzaCompleta, "VOCE-2", 2, 60.0, "Quota B", "NON_ESEGUITO");

        // 2) Pendenza senza avviso (per testare assenza di _links.avviso)
        newPendenza("PEND-NO-AVV", domVisibile, app, tv, tvdVis, null,
                "VRDLGI90B02H501T", null,
                50.0, "NON_ESEGUITO", 2, null, null);

        // 3) Pendenza pagata (per testare presenza di _links.ricevuta)
        newPendenza("PEND-PAGATA", domVisibile, app, tv, tvdVis, null,
                "BNCMRA70C03H501S", "001999999999999999",
                75.0, "ESEGUITA", 3, null, null);

        // 4) Pendenza nel dominio NON visibile (per testare 404 anti-leak)
        newPendenza("PEND-HIDDEN", domNonVisibile, app, tv, tvdNon, null,
                "GLGFNC85D04H501R", null,
                200.0, "NON_ESEGUITO", 4, null, null);

        // 5) Pendenza con voci di tipo diverso (dettaglio oneOf) + multibeneficiario.
        Versamento pendenzaVoci = newPendenza("PEND-VOCI", domVisibile, app, tv, tvdVis, null,
                "RSSMRA80A01H501U", null, 60.0, "NON_ESEGUITO", 5, null, null);
        // ENTRATA_ANAGRAFICA: voce con riferimento a tributo (cod entrata via tipi_tributo).
        TipoTributo tipoTributo = new TipoTributo();
        tipoTributo.setCodTributo("TARI-2026");
        em.persist(tipoTributo);
        Tributo tributo = new Tributo();
        tributo.setTipoTributo(tipoTributo);
        em.persist(tributo);
        SingoloVersamento vEntrata = newVoce(pendenzaVoci, "VOCE-ENTRATA", 1, 20.0);
        vEntrata.setTributo(tributo);
        singoloVersamentoRepository.save(vEntrata);
        // INCASSO_DIRETTO: voce con iban + contabilita, su dominio diverso (multibeneficiario).
        IbanAccredito iban = new IbanAccredito();
        iban.setPostale(false);
        iban.setCodIban("IT60X0542811101000000123456");
        iban.setDominio(domNonVisibile);
        em.persist(iban);
        SingoloVersamento vIncasso = newVoce(pendenzaVoci, "VOCE-INCASSO", 2, 20.0);
        vIncasso.setIbanAccredito(iban);
        vIncasso.setTipoContabilita("0");
        vIncasso.setCodiceContabilita("3321");
        vIncasso.setDominio(domNonVisibile);
        singoloVersamentoRepository.save(vIncasso);
        // BOLLO_TELEMATICO: voce con i tre campi del bollo.
        SingoloVersamento vBollo = newVoce(pendenzaVoci, "VOCE-BOLLO", 3, 20.0);
        vBollo.setTipoBollo("01");
        vBollo.setHashDocumento("aGFzaC1iYXNlNjQ=");
        vBollo.setProvinciaResidenza("RM");
        singoloVersamentoRepository.save(vBollo);
        // Voce legacy ambigua (bollo + iban): la priorita' deve scegliere BOLLO_TELEMATICO.
        IbanAccredito ibanAmbiguo = new IbanAccredito();
        ibanAmbiguo.setPostale(false);
        ibanAmbiguo.setCodIban("IT60X0542811101000000999999");
        ibanAmbiguo.setDominio(domNonVisibile);
        em.persist(ibanAmbiguo);
        SingoloVersamento vAmbigua = newVoce(pendenzaVoci, "VOCE-AMBIGUA", 4, 0.0);
        vAmbigua.setTipoBollo("01");
        vAmbigua.setHashDocumento("aGFzaA==");
        vAmbigua.setProvinciaResidenza("MI");
        vAmbigua.setIbanAccredito(ibanAmbiguo);
        singoloVersamentoRepository.save(vAmbigua);

        // Forza l'INSERT delle voci e libera il primo livello: senza questo, la
        // collection LAZY del Versamento resta vuota in cache anche se le voci sono
        // state salvate via repository.
        em.flush();
        em.clear();
    }

    private Dominio saveDominio(String cod, String rs) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(rs);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private TipoVersamentoDominio saveTvd(Dominio d, TipoVersamento tv) {
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(d);
        tvd.setTipoVersamento(tv);
        return tipoVersamentoDominioRepository.save(tvd);
    }

    private Versamento newPendenza(String idPendenza, Dominio dom, Applicazione app, TipoVersamento tv,
                                   TipoVersamentoDominio tvd, UnitaOperativa uo, String debitore,
                                   String numAvviso, double importo, String statoV1,
                                   int orderOffsetHours, String datiAllegati, String proprieta) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(importo);
        v.setStatoVersamento(statoV1);
        v.setDataCreazione(OffsetDateTime.now().minusHours(orderOffsetHours));
        v.setDataOraUltimoAggiornamento(OffsetDateTime.now().minusHours(orderOffsetHours));
        v.setCausaleVersamento("Causale per " + idPendenza);
        v.setDebitoreIdentificativo(debitore);
        v.setDebitoreAnagrafica("Anagrafica " + debitore);
        v.setSrcDebitoreIdentificativo(debitore);
        v.setImportoPagato(0.0);
        v.setAnomalo(false);
        v.setAck(false);
        v.setTipo("DOVUTO");
        v.setNumeroAvviso(numAvviso);
        v.setTassonomia("tax-A");
        v.setTassonomiaAvviso("tax-avv");
        v.setDirezione("Dir-X");
        v.setDivisione("Div-Y");
        v.setDatiAllegati(datiAllegati);
        v.setProprieta(proprieta);
        v.setDominio(dom);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        v.setUnitaOperativa(uo);
        return versamentoRepository.save(v);
    }

    private void addVoce(Versamento v, String idVoce, int indice, double importo,
                         String descrizione, String statoV1) {
        SingoloVersamento sv = new SingoloVersamento();
        sv.setCodSingoloVersamentoEnte(idVoce);
        sv.setIndiceDati(indice);
        sv.setImportoSingoloVersamento(importo);
        sv.setDescrizione(descrizione);
        sv.setStatoSingoloVersamento(statoV1);
        sv.setVersamento(v);
        singoloVersamentoRepository.save(sv);
    }

    private SingoloVersamento newVoce(Versamento v, String idVoce, int indice, double importo) {
        SingoloVersamento sv = new SingoloVersamento();
        sv.setCodSingoloVersamentoEnte(idVoce);
        sv.setIndiceDati(indice);
        sv.setImportoSingoloVersamento(importo);
        sv.setDescrizione(idVoce);
        sv.setStatoSingoloVersamento("NON_ESEGUITO");
        sv.setVersamento(v);
        return sv;
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void vociExposeDettaglioOneOfPerType() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-VOCI").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci", hasSize(4)))
                // VOCE-ENTRATA → ENTRATA_ANAGRAFICA con codEntrata dal tipo tributo.
                .andExpect(jsonPath("$.voci[0].dettaglio.tipoVoce", is("ENTRATA_ANAGRAFICA")))
                .andExpect(jsonPath("$.voci[0].dettaglio.codEntrata", is("TARI-2026")))
                .andExpect(jsonPath("$.voci[0].descrizione", is("VOCE-ENTRATA")))
                // VOCE-INCASSO → INCASSO_DIRETTO con iban + contabilita.
                .andExpect(jsonPath("$.voci[1].dettaglio.tipoVoce", is("INCASSO_DIRETTO")))
                .andExpect(jsonPath("$.voci[1].dettaglio.ibanAccredito", is("IT60X0542811101000000123456")))
                .andExpect(jsonPath("$.voci[1].dettaglio.tipoContabilita", is("CAPITOLO")))
                .andExpect(jsonPath("$.voci[1].dettaglio.codiceContabilita", is("3321")))
                // VOCE-BOLLO → BOLLO_TELEMATICO.
                .andExpect(jsonPath("$.voci[2].dettaglio.tipoVoce", is("BOLLO_TELEMATICO")))
                .andExpect(jsonPath("$.voci[2].dettaglio.provinciaResidenza", is("RM")))
                // VOCE-AMBIGUA (bollo + iban) → la priorita' sceglie BOLLO_TELEMATICO.
                .andExpect(jsonPath("$.voci[3].dettaglio.tipoVoce", is("BOLLO_TELEMATICO")));
    }

    @Test
    void multibeneficiarioVoceHasOwnDominio() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-VOCI").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                // VOCE-INCASSO ha dominio diverso dalla pendenza → valorizzato.
                .andExpect(jsonPath("$.voci[1].dominio.idDominio", is("22222222222")))
                // VOCE-ENTRATA senza dominio proprio → assente (single-beneficiario).
                .andExpect(jsonPath("$.voci[0].dominio").doesNotExist());
    }

    @Test
    void returnsDetailWithVociAndLinks() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idA2A", is(APP_COD)))
                .andExpect(jsonPath("$.idPendenza", is("PEND-FULL")))
                .andExpect(jsonPath("$.stato", is("NON_PAGATA")))
                .andExpect(jsonPath("$.direzione", is("Dir-X")))
                .andExpect(jsonPath("$.divisione", is("Div-Y")))
                .andExpect(jsonPath("$.voci", hasSize(2)))
                .andExpect(jsonPath("$.voci[*].idVocePendenza", contains("VOCE-1", "VOCE-2")))
                .andExpect(jsonPath("$.voci[*].indice", contains(1, 2)))
                .andExpect(jsonPath("$._links.informazioniDebitore.href",
                        endsWith("/pendenze/" + APP_COD + "/PEND-FULL/informazioniDebitore")))
                .andExpect(jsonPath("$._links.ricevute.href",
                        endsWith("/pendenze/" + APP_COD + "/PEND-FULL/ricevute")))
                .andExpect(jsonPath("$._links.avviso.href",
                        endsWith("/pendenze/" + APP_COD + "/PEND-FULL/avviso")))
                // Campi rimossi dal refactor #9.
                .andExpect(jsonPath("$.tipo").doesNotExist())
                .andExpect(jsonPath("$.tassonomia").doesNotExist())
                .andExpect(jsonPath("$.tassonomiaAvviso").doesNotExist())
                .andExpect(jsonPath("$._links.ricevuta").doesNotExist());
    }

    @Test
    void linksAvvisoAbsentWhenNoNumeroAvviso() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-NO-AVV").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.informazioniDebitore.href", notNullValue()))
                .andExpect(jsonPath("$._links.ricevute.href", notNullValue()))
                .andExpect(jsonPath("$._links.avviso").doesNotExist());
    }

    @Test
    void linksRicevuteAlwaysPresent() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-PAGATA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato", is("PAGATA")))
                .andExpect(jsonPath("$._links.ricevute.href",
                        endsWith("/pendenze/" + APP_COD + "/PEND-PAGATA/ricevute")))
                .andExpect(jsonPath("$._links.ricevuta").doesNotExist());
    }

    @Test
    void returns404WhenPendenzaDoesNotExist() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-INESISTENTE").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void returns404OnHiddenDomainAntiLeak() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-HIDDEN").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void expandDatiAllegatiPopulatesField() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL")
                        .param("expand", "datiAllegati")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datiAllegati", containsString("valore-allegato")))
                .andExpect(jsonPath("$.proprieta").doesNotExist());
    }

    @Test
    void expandProprietaPopulatesField() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL")
                        .param("expand", "proprieta")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proprieta", containsString("valore-prop")))
                .andExpect(jsonPath("$.datiAllegati").doesNotExist());
    }

    @Test
    void expandBothPopulatesBothFields() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL")
                        .param("expand", "datiAllegati,proprieta")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datiAllegati", containsString("valore-allegato")))
                .andExpect(jsonPath("$.proprieta", containsString("valore-prop")));
    }

    @Test
    void expandUnknownReturns400() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL")
                        .param("expand", "bogus")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void unsupportedQueryParamReturns400() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL")
                        .param("foo", "bar")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Filtro non supportato")));
    }

    @Test
    void defaultRequestDoesNotIncludeExpandFields() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datiAllegati").doesNotExist())
                .andExpect(jsonPath("$.proprieta").doesNotExist())
                .andExpect(jsonPath("$.causale", is("Causale per PEND-FULL")))
                .andExpect(jsonPath("$.unitaOperativa.idUnitaOperativa", is("UO1")))
                .andExpect(jsonPath("$.idDebitore").doesNotExist());
    }

    @Test
    void detailResponseHasNoSoggettoField() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                // Lo schema Pendenza non include soggettoPagatore: i dati anagrafici
                // sono solo su /informazioniDebitore.
                .andExpect(jsonPath("$.soggettoPagatore").doesNotExist())
                .andExpect(jsonPath("$.debitoreEmail").doesNotExist())
                .andExpect(jsonPath("$.debitoreCellulare").doesNotExist());
    }

    @Test
    void detailResponseHasNoUnsupportedLinks() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-PAGATA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.rpp").doesNotExist())
                .andExpect(jsonPath("$._links.pagamenti").doesNotExist())
                .andExpect(jsonPath("$._links.allegati").doesNotExist());
    }

    @Test
    void vociAreOrderedByIndice() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci[0].indice", is(1)))
                .andExpect(jsonPath("$.voci[1].indice", is(2)))
                .andExpect(jsonPath("$.voci[0].stato", is("NON_PAGATA")))
                .andExpect(jsonPath("$.voci[0].descrizione", is("Quota A")))
                .andExpect(jsonPath("$.voci[1].descrizione", is("Quota B")));
    }

    @Test
    void pendenzaWithoutUnitaOperativaSerializesNull() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-NO-AVV").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitaOperativa", nullValue()));
    }
}
