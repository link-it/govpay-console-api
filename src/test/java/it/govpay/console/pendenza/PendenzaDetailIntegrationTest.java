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

import java.math.BigDecimal;
import java.time.LocalDate;
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
import it.govpay.console.entity.TipoTributo;
import it.govpay.console.entity.Tributo;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.common.entity.TipoContabilita;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.model.DettaglioContabile;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
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
    private static final String COD_DOMINIO_VISIBILE = "11111111111";

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
    private ConsoleDominioRepository dominioRepository;
    @Autowired
    private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private UnitaOperativaRepository unitaOperativaRepository;
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

        Dominio domVisibile = saveDominio(COD_DOMINIO_VISIBILE, "Dominio Visibile");
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

        UnitaOperativa uo = saveUo("UO1", "Ufficio Tributi", domVisibile);
        UnitaOperativa uoEc = saveUo("EC", "Ente creditore", domVisibile);

        // 1) Pendenza completa con voci, avviso e dati allegati / proprieta
        Dati datiCompleta = pendenza(app, domVisibile, tv, tvdVis, uo, "PEND-FULL",
                "001234567890123456", "RSSMRA80A01H501U", 100.0, StatoPendenza.NON_ESEGUITO, 1)
                .conDatiAllegati("{\"key\":\"valore-allegato\"}", "{\"prop\":\"valore-prop\"}")
                .conScadenza(OffsetDateTime.now().plusDays(30));
        Pendenza full = PendenzaV3Fixtures.pendenza(em, datiCompleta);
        PendenzaV3Fixtures.voce(em, full, "VOCE-1", 1, 40.0, "Quota A", voce -> {
            voce.setDatiAllegati("{\"voce\":\"allegato\"}");
            voce.setMetadata("{\"voce\":\"metadata\"}");
            voce.setDescrizioneCausaleRpt("Causale RPT");
            voce.setDettaglioContabile(java.util.List.of(new DettaglioContabile.Civilistico(
                    "2026", "UFF1", "14.01.03", null, null, new BigDecimal("40.00"))));
        });
        PendenzaV3Fixtures.voce(em, full, "VOCE-2", 2, 60.0, "Quota B", null);

        // 2) Pendenza senza avviso (per testare assenza di _links.avviso)
        PendenzaV3Fixtures.pendenza(em, pendenza(app, domVisibile, tv, tvdVis, null, "PEND-NO-AVV",
                null, "VRDLGI90B02H501T", 50.0, StatoPendenza.NON_ESEGUITO, 2));

        // 3) Pendenza pagata (per testare presenza di _links.ricevuta)
        PendenzaV3Fixtures.pendenza(em, pendenza(app, domVisibile, tv, tvdVis, null, "PEND-PAGATA",
                "001999999999999999", "BNCMRA70C03H501S", 75.0, StatoPendenza.ESEGUITO, 3));

        // 4) Pendenza nel dominio NON visibile (per testare 404 anti-leak)
        PendenzaV3Fixtures.pendenza(em, pendenza(app, domNonVisibile, tv, tvdNon, null, "PEND-HIDDEN",
                null, "GLGFNC85D04H501R", 200.0, StatoPendenza.NON_ESEGUITO, 4));

        // 5) Pendenza con voci di tipo diverso (dettaglio oneOf) + multibeneficiario.
        Pendenza voci = PendenzaV3Fixtures.pendenza(em, pendenza(app, domVisibile, tv, tvdVis, null, "PEND-VOCI",
                null, "RSSMRA80A01H501U", 60.0, StatoPendenza.NON_ESEGUITO, 5));
        // ENTRATA_ANAGRAFICA: voce con riferimento a tributo (cod entrata via tipi_tributo).
        TipoTributo tipoTributo = new TipoTributo();
        tipoTributo.setCodTributo("TARI-2026");
        em.persist(tipoTributo);
        Tributo tributo = new Tributo();
        tributo.setTipoTributo(tipoTributo);
        tributo.setDominio(domVisibile);
        em.persist(tributo);
        PendenzaV3Fixtures.voce(em, voci, "VOCE-ENTRATA", 1, 20.0, "VOCE-ENTRATA",
                voce -> voce.setIdTributo(tributo.getId()));
        // INCASSO_DIRETTO: voce con iban + contabilita, su dominio diverso (multibeneficiario).
        IbanAccredito iban = saveIban("IT60X0542811101000000123456", domNonVisibile);
        PendenzaV3Fixtures.voce(em, voci, "VOCE-INCASSO", 2, 20.0, "VOCE-INCASSO", voce -> {
            voce.setIdIbanAccredito(iban.getId());
            voce.setTipoContabilita(TipoContabilita.CAPITOLO);
            voce.setCodiceContabilita("3321");
            voce.setIdDominio(domNonVisibile.getId());
        });
        // BOLLO_TELEMATICO: voce con i tre campi del bollo.
        PendenzaV3Fixtures.voce(em, voci, "VOCE-BOLLO", 3, 20.0, "VOCE-BOLLO", voce -> {
            voce.setTipoBollo("01");
            voce.setHashDocumento("aGFzaC1iYXNlNjQ=");
            voce.setProvinciaResidenza("RM");
        });
        // Voce ambigua (bollo + iban): il bollo prevale.
        IbanAccredito ibanAmbiguo = saveIban("IT60X0542811101000000999999", domNonVisibile);
        PendenzaV3Fixtures.voce(em, voci, "VOCE-AMBIGUA", 4, 0.0, "VOCE-AMBIGUA", voce -> {
            voce.setTipoBollo("01");
            voce.setHashDocumento("aGFzaA==");
            voce.setProvinciaResidenza("MI");
            voce.setIdIbanAccredito(ibanAmbiguo.getId());
        });

        // 6) Pendenza non eseguita con scadenza passata
        PendenzaV3Fixtures.pendenza(em, pendenza(app, domVisibile, tv, tvdVis, null, "PEND-SCADUTA",
                "001000000000000001", "RSSMRA80A01H501U", 10.0, StatoPendenza.NON_ESEGUITO, 6)
                .conScadenza(OffsetDateTime.now().minusDays(1)));

        // 7) Pendenza la cui posizione sara' pubblicata domani, con scadenza gia' passata
        PendenzaV3Fixtures.pendenza(em, pendenza(app, domVisibile, tv, tvdVis, null, "PEND-NON-PUBBL",
                "001000000000000002", "RSSMRA80A01H501U", 10.0, StatoPendenza.NON_ESEGUITO, 7)
                .conPubblicazione(LocalDate.now().plusDays(1))
                .conScadenza(OffsetDateTime.now().minusDays(1)));

        // 8) Pendenza assegnata all'unita' operativa dell'ente creditore
        PendenzaV3Fixtures.pendenza(em, pendenza(app, domVisibile, tv, tvdVis, uoEc, "PEND-EC",
                "001000000000000003", "RSSMRA80A01H501U", 10.0, StatoPendenza.NON_ESEGUITO, 8));

        // 9) Rata di un piano rateale
        PendenzaV3Fixtures.pendenza(em, pendenza(app, domVisibile, tv, tvdVis, null, "PEND-RATA",
                "001000000000000004", "RSSMRA80A01H501U", 10.0, StatoPendenza.NON_ESEGUITO, 9)
                .conTipologia(TipologiaOpzionePagamento.PIANO_RATEALE));

        // 9b) Pendenza del secondo debitore della posizione, e di un debitore rimosso
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, pendenza(app, domVisibile, tv, tvdVis, null, "PEND-SECONDO-DEB",
                "001000000000000006", "01234567890", 10.0, StatoPendenza.NON_ESEGUITO, 10), "RSSMRA80A01H501U", false);
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em, pendenza(app, domVisibile, tv, tvdVis, null, "PEND-DEB-RIMOSSO",
                "001000000000000007", "01234567890", 10.0, StatoPendenza.NON_ESEGUITO, 10), "RSSMRA80A01H501U", true);

        // 10) Pendenza senza opzione di pagamento: il modello non la ammette
        PendenzaV3Fixtures.pendenzaSenzaOpzione(em, pendenza(app, domVisibile, tv, tvdVis, null, "PEND-SENZA-OPZIONE",
                "001000000000000005", "RSSMRA80A01H501U", 10.0, StatoPendenza.NON_ESEGUITO, 10));

        // Forza l'INSERT e libera il primo livello, cosi' le letture ricaricano dal database.
        em.flush();
        em.clear();
    }

    private Dati pendenza(Applicazione app, Dominio dominio, TipoVersamento tv, TipoVersamentoDominio tvd,
                          UnitaOperativa uo, String idPendenza, String numeroAvviso, String debitore,
                          double importo, StatoPendenza stato, int oreDaOra) {
        return new Dati(app.getId(), dominio.getId(), tv.getId(), tvd.getId(),
                uo != null ? uo.getId() : null, idPendenza, numeroAvviso, debitore, importo, stato, oreDaOra);
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

    private UnitaOperativa saveUo(String cod, String denominazione, Dominio dominio) {
        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo(cod);
        uo.setUoDenominazione(denominazione);
        uo.setDominio(dominio);
        return unitaOperativaRepository.save(uo);
    }

    private IbanAccredito saveIban(String codIban, Dominio dominio) {
        IbanAccredito iban = new IbanAccredito();
        iban.setPostale(false);
        iban.setCodIban(codIban);
        iban.setDominio(dominio);
        em.persist(iban);
        return iban;
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
                .andExpect(jsonPath("$.stato", is("NON_ESEGUITO")))
                .andExpect(jsonPath("$.dominio.idDominio", is(COD_DOMINIO_VISIBILE)))
                .andExpect(jsonPath("$.dominio.ragioneSociale", is("Dominio Visibile")))
                .andExpect(jsonPath("$.tipoPendenza.idTipoPendenza", is("TARI")))
                .andExpect(jsonPath("$.tipoPendenza.descrizione", is("Tassa rifiuti")))
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
                .andExpect(jsonPath("$.tipo").doesNotExist())
                .andExpect(jsonPath("$.tassonomia").doesNotExist())
                .andExpect(jsonPath("$.tassonomiaAvviso").doesNotExist())
                .andExpect(jsonPath("$._links.ricevuta").doesNotExist());
    }

    @Test
    void opzioneDiPagamentoEPosizioneDebitoria() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opzionePagamento.idOpzionePagamento", notNullValue()))
                .andExpect(jsonPath("$.opzionePagamento.tipologia", is("SOLUZIONE_UNICA")))
                .andExpect(jsonPath("$.opzionePagamento.stato", is("DISPONIBILE")))
                .andExpect(jsonPath("$.opzionePagamento.numeroRata").doesNotExist())
                .andExpect(jsonPath("$.posizioneDebitoria.idPosizioneDebitoria",
                        is(APP_COD + COD_DOMINIO_VISIBILE + "DOC-PEND-FULL")))
                .andExpect(jsonPath("$.posizioneDebitoria.dataPubblicazione").doesNotExist());
    }

    @Test
    void rataDiPianoRateale() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-RATA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opzionePagamento.tipologia", is("PIANO_RATEALE")))
                .andExpect(jsonPath("$.opzionePagamento.numeroRata", is(1)));
    }

    @Test
    void statoScadutoPerNonEseguitaConScadenzaPassata() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SCADUTA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato", is("SCADUTO")));
    }

    @Test
    void statoNonPubblicataPrevaleSuScaduto() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-NON-PUBBL").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato", is("NON_PUBBLICATA")))
                .andExpect(jsonPath("$.posizioneDebitoria.dataPubblicazione",
                        is(LocalDate.now().plusDays(1).toString())));
    }

    @Test
    void unitaOperativaDelEnteCreditoreNonSiEspone() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-EC").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitaOperativa", nullValue()));
    }

    @Test
    void voceEspoteContabilitaDatiAllegatiEMetadata() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voci[0].datiAllegati", containsString("allegato")))
                .andExpect(jsonPath("$.voci[0].metadata", containsString("metadata")))
                .andExpect(jsonPath("$.voci[0].descrizioneCausaleRPT", is("Causale RPT")))
                .andExpect(jsonPath("$.voci[0].contabilita", containsString("14.01.03")))
                .andExpect(jsonPath("$.voci[1].datiAllegati").doesNotExist())
                .andExpect(jsonPath("$.voci[1].contabilita").doesNotExist());
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
                .andExpect(jsonPath("$.stato", is("ESEGUITO")))
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
    void returns404WhenPendenzaHasNoOpzionePagamento() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SENZA-OPZIONE").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
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
                .andExpect(jsonPath("$.unitaOperativa.ragioneSociale", is("Ufficio Tributi")))
                .andExpect(jsonPath("$.idDebitore").doesNotExist());
    }

    @Test
    void debitoreIsTheOneOfTheOptionNotTheFirstOfThePosition() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SECONDO-DEB").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debitore.tipo", is("G")))
                .andExpect(jsonPath("$.debitore.identificativo", is("01234567890")));
    }

    @Test
    void removedDebitoreKeepsItsRiferimentoAndAnnulledOption() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-DEB-RIMOSSO").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debitore.identificativo", is("01234567890")))
                .andExpect(jsonPath("$.opzionePagamento.stato", is("ANNULLATA")));
    }

    @Test
    void detailExposesOnlyTipoAndIdentificativoOfDebitore() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-FULL").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debitore.tipo", is("F")))
                .andExpect(jsonPath("$.debitore.identificativo", notNullValue()))
                // Anagrafica e contatti sono solo su /informazioniDebitore.
                .andExpect(jsonPath("$.debitore.anagrafica").doesNotExist())
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
                .andExpect(jsonPath("$.voci[0].stato", is("NON_ESEGUITO")))
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
