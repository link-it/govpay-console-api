package it.govpay.console.impostazioni;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.configurazione.ConfigurazioneKeys;
import it.govpay.common.configurazione.model.Hardening;
import it.govpay.common.configurazione.model.MailBatch;
import it.govpay.console.connettore.ConnettoreProprietaKeys;
import it.govpay.console.connettore.ConnettoreStore;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.AreaImpostazioni;
import it.govpay.console.model.ImpostazioniOverview;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.security.AclAuthorizer;

/**
 * Overview delle 8 sotto-risorse indipendenti del blob V1 {@code configurazione}.
 * `abilitata` e' popolato solo per le aree con un vero flag di abilitazione
 * (connettori EAV e hardening); le aree di solo template non lo espongono.
 * `ultimaModifica` e' derivata da {@code gp_audit} (nessuna colonna dedicata
 * nelle tabelle: V1 non tracciava affatto questa informazione).
 */
@Service
public class ImpostazioniOverviewService {

    private final ConnettoreStore connettoreStore;
    private final ConfigurazioneBlobStore blobStore;
    private final GpAuditRepository gpAuditRepository;
    private final AclAuthorizer aclAuthorizer;

    public ImpostazioniOverviewService(ConnettoreStore connettoreStore,
                                       ConfigurazioneBlobStore blobStore,
                                       GpAuditRepository gpAuditRepository,
                                       AclAuthorizer aclAuthorizer) {
        this.connettoreStore = connettoreStore;
        this.blobStore = blobStore;
        this.gpAuditRepository = gpAuditRepository;
        this.aclAuthorizer = aclAuthorizer;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ImpostazioniOverview> get() {
        aclAuthorizer.requireLettura(AclServizio.CONFIGURAZIONE_E_MANUTENZIONE);

        ImpostazioniOverview overview = new ImpostazioniOverview();
        overview.setAree(List.of(
                areaConnettore("servizioGDE", "Servizio GDE", "/impostazioni/servizioGDE",
                        ServizioGdeService.COD_CONNETTORE_GDE,
                        List.of(ServizioGdeService.AZIONE_AUDIT_MODIFICA, ServizioGdeService.AZIONE_AUDIT_CREDENZIALI)),
                areaSoloTemplate("giornale-eventi", "Giornale degli Eventi", "/impostazioni/giornale-eventi",
                        List.of(GiornaleEventiService.AZIONE_AUDIT_MODIFICA)),
                areaMailServer(),
                areaSoloTemplate("mail-template-promemoria", "Template promemoria (mail)",
                        "/impostazioni/mail/template-promemoria",
                        List.of(MailPromemoriaService.AZIONE_AUDIT_MODIFICA)),
                areaConnettore("app-io-server", "Server App IO", "/impostazioni/app-io/server",
                        AppIoServerService.COD_CONNETTORE_APP_IO,
                        List.of(AppIoServerService.AZIONE_AUDIT_MODIFICA, AppIoServerService.AZIONE_AUDIT_CREDENZIALI)),
                areaSoloTemplate("app-io-template-promemoria", "Template promemoria (App IO)",
                        "/impostazioni/app-io/template-promemoria",
                        List.of(AppIoPromemoriaService.AZIONE_AUDIT_MODIFICA)),
                areaSoloTemplate("tracciati-csv", "Tracciati CSV", "/impostazioni/tracciati-csv",
                        List.of(TracciatiCsvService.AZIONE_AUDIT_MODIFICA)),
                areaHardening()));
        return ResponseEntity.ok(overview);
    }

    private AreaImpostazioni areaConnettore(String codice, String nome, String href, String codConnettore,
                                            List<String> azioniAudit) {
        boolean abilitata = Boolean.parseBoolean(
                connettoreStore.read(codConnettore).get(ConnettoreProprietaKeys.ABILITATO));
        return area(codice, nome, href, abilitata, azioniAudit);
    }

    private AreaImpostazioni areaMailServer() {
        boolean abilitata = blobStore.read(ConfigurazioneKeys.KEY_MAIL_BATCH, MailBatch.class, MailBatch::new)
                .isAbilitato();
        return area("mail-server", "Server SMTP", "/impostazioni/mail/server", abilitata,
                List.of(MailServerService.AZIONE_AUDIT_MODIFICA, MailServerService.AZIONE_AUDIT_CREDENZIALI));
    }

    private AreaImpostazioni areaHardening() {
        boolean abilitata = blobStore.read(ConfigurazioneKeys.KEY_HARDENING, Hardening.class, Hardening::new)
                .isAbilitato();
        return area("hardening", "Hardening (reCAPTCHA)", "/impostazioni/hardening", abilitata,
                List.of(HardeningService.AZIONE_AUDIT_MODIFICA, HardeningService.AZIONE_AUDIT_CREDENZIALI));
    }

    private AreaImpostazioni areaSoloTemplate(String codice, String nome, String href, List<String> azioniAudit) {
        AreaImpostazioni area = new AreaImpostazioni(codice, nome, href);
        area.setUltimaModifica(ultimaModifica(azioniAudit));
        return area;
    }

    private AreaImpostazioni area(String codice, String nome, String href, boolean abilitata, List<String> azioniAudit) {
        AreaImpostazioni area = new AreaImpostazioni(codice, nome, href);
        area.setAbilitata(abilitata);
        area.setUltimaModifica(ultimaModifica(azioniAudit));
        return area;
    }

    private java.time.OffsetDateTime ultimaModifica(List<String> azioni) {
        return gpAuditRepository.findUltimaModifica(azioni).orElse(null);
    }
}
