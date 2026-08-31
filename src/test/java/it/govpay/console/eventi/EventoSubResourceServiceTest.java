package it.govpay.console.eventi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.govpay.console.audit.AuditService;
import it.govpay.console.model.EventoHeader;
import it.govpay.console.model.EventoRichiesta;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotFoundException;
import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.Evento;
import it.govpay.gde.client.beans.Header;

class EventoSubResourceServiceTest {

    private EventoGdeClient client;
    private EventoAcl eventoAcl;
    private CurrentOperatorService currentOperatorService;
    private AuditService auditService;
    private EventoSubResourceService service;

    private final OperatoreCorrente operatore = new OperatoreCorrente(
            "op1", 1L, 1L, "Operatore Uno", true, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
            true, java.util.Set.of());

    @BeforeEach
    void setup() {
        client = mock(EventoGdeClient.class);
        eventoAcl = mock(EventoAcl.class);
        currentOperatorService = mock(CurrentOperatorService.class);
        auditService = mock(AuditService.class);

        when(currentOperatorService.get()).thenReturn(operatore);
        when(eventoAcl.isVisibile(any(), any())).thenReturn(true);

        service = new EventoSubResourceService(client, eventoAcl, currentOperatorService, auditService,
                "Authorization,Proxy-Authorization,Cookie,Set-Cookie,X-Api-Key,X-Auth-Token,X-Csrf-Token");
    }

    @Test
    void getRichiesta_default_mascheraHeaderSensibili() {
        when(client.getEventoById(1L)).thenReturn(new Evento()
                .id(1L)
                .parametriRichiesta(new DettaglioRichiesta()
                        .headers(List.of(
                                new Header().nome("Authorization").valore("Basic xxx"),
                                new Header().nome("X-Request-Id").valore("abc")))
                        .payload("aGVsbG8=")));

        EventoRichiesta dto = service.getRichiesta(1L, false, null);

        EventoHeader auth = dto.getHeaders().stream().filter(h -> h.getNome().equals("Authorization")).findFirst().orElseThrow();
        assertThat(auth.getValore()).isEqualTo("***REDACTED***");
        assertThat(auth.getRedatto()).isTrue();

        EventoHeader reqId = dto.getHeaders().stream().filter(h -> h.getNome().equals("X-Request-Id")).findFirst().orElseThrow();
        assertThat(reqId.getValore()).isEqualTo("abc");
        assertThat(reqId.getRedatto()).isFalse();

        assertThat(dto.getPayload()).isEqualTo("hello");
    }

    @Test
    void getRichiesta_matchHeaderCaseInsensitive() {
        when(client.getEventoById(1L)).thenReturn(new Evento()
                .id(1L)
                .parametriRichiesta(new DettaglioRichiesta()
                        .headers(List.of(new Header().nome("authorization").valore("Bearer yyy")))));

        EventoRichiesta dto = service.getRichiesta(1L, false, null);

        assertThat(dto.getHeaders().get(0).getRedatto()).isTrue();
        assertThat(dto.getHeaders().get(0).getValore()).isEqualTo("***REDACTED***");
    }

    /**
     * `nome` e' obbligatorio sullo schema di GDE, ma arriva da una risposta HTTP e
     * sulle risposte non gira alcuna Bean Validation. Un header senza nome non e'
     * sensibile e non deve far esplodere il dettaglio dell'evento.
     */
    @Test
    void getRichiesta_headerSenzaNomeNonEsplodeENonEMascherato() {
        when(client.getEventoById(1L)).thenReturn(new Evento()
                .id(1L)
                .parametriRichiesta(new DettaglioRichiesta()
                        .headers(List.of(new Header().valore("valore-senza-nome")))));

        EventoRichiesta dto = service.getRichiesta(1L, false, null);

        assertThat(dto.getHeaders()).hasSize(1);
        assertThat(dto.getHeaders().get(0).getNome()).isNull();
        assertThat(dto.getHeaders().get(0).getRedatto()).isFalse();
        assertThat(dto.getHeaders().get(0).getValore()).isEqualTo("valore-senza-nome");
    }

    @Test
    void getRichiesta_unmask_mostraValoriInChiaro() {
        when(client.getEventoById(1L)).thenReturn(new Evento()
                .id(1L)
                .parametriRichiesta(new DettaglioRichiesta()
                        .headers(List.of(new Header().nome("Authorization").valore("Basic xxx")))));

        EventoRichiesta dto = service.getRichiesta(1L, true, null);

        assertThat(dto.getHeaders().get(0).getValore()).isEqualTo("Basic xxx");
        assertThat(dto.getHeaders().get(0).getRedatto()).isFalse();
    }

    @Test
    void getRichiesta_audit_scrittoUnaVoltaSenzaUnmask() {
        when(client.getEventoById(1L)).thenReturn(new Evento()
                .id(1L)
                .parametriRichiesta(new DettaglioRichiesta().headers(List.of(new Header().nome("X").valore("Y")))));

        service.getRichiesta(1L, false, null);

        verify(auditService, times(1)).registra(eq(EventoSubResourceService.AZIONE_AUDIT_RICHIESTA),
                eq(1L), any(), eq(operatore), any());
        verify(auditService, never()).registra(eq(EventoSubResourceService.AZIONE_AUDIT_CREDENZIALI),
                anyLong(), any(), any(), any());
    }

    @Test
    void getRichiesta_audit_scriveAncheCredenzialiConUnmask() {
        when(client.getEventoById(1L)).thenReturn(new Evento()
                .id(1L)
                .parametriRichiesta(new DettaglioRichiesta().headers(List.of(new Header().nome("X").valore("Y")))));

        service.getRichiesta(1L, true, null);

        verify(auditService, times(1)).registra(eq(EventoSubResourceService.AZIONE_AUDIT_RICHIESTA),
                eq(1L), any(), eq(operatore), any());
        verify(auditService, times(1)).registra(eq(EventoSubResourceService.AZIONE_AUDIT_CREDENZIALI),
                eq(1L), any(), eq(operatore), any());
    }

    @Test
    void getRichiesta_nonRegistrata_lancia404_senzaAudit() {
        when(client.getEventoById(1L)).thenReturn(new Evento().id(1L));

        assertThatThrownBy(() -> service.getRichiesta(1L, false, null)).isInstanceOf(NotFoundException.class);

        verify(auditService, never()).registra(any(), anyLong(), any(), any(), any());
    }

    @Test
    void getRichiesta_headerSenzaPayload_eComunqueRegistrata() {
        when(client.getEventoById(1L)).thenReturn(new Evento()
                .id(1L)
                .parametriRichiesta(new DettaglioRichiesta().headers(List.of(new Header().nome("X").valore("Y")))));

        EventoRichiesta dto = service.getRichiesta(1L, false, null);

        assertThat(dto.getPayload()).isNull();
        assertThat(dto.getHeaders()).hasSize(1);
    }

    @Test
    void getRichiesta_aclNegata_lancia404() {
        when(client.getEventoById(1L)).thenReturn(new Evento().id(1L).idDominio("99999999999"));
        when(eventoAcl.isVisibile(eq("99999999999"), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getRichiesta(1L, false, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getRisposta_simmetricaARichiesta() {
        when(client.getEventoById(2L)).thenReturn(new Evento()
                .id(2L)
                .parametriRisposta(new DettaglioRisposta()
                        .headers(List.of(new Header().nome("Set-Cookie").valore("JSESSIONID=abc")))
                        .payload("d29ybGQ=")));

        var dto = service.getRisposta(2L, false, null);

        assertThat(dto.getHeaders().get(0).getRedatto()).isTrue();
        assertThat(dto.getPayload()).isEqualTo("world");
        verify(auditService, times(1)).registra(eq(EventoSubResourceService.AZIONE_AUDIT_RISPOSTA),
                eq(2L), any(), eq(operatore), any());
    }
}
