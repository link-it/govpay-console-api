package it.govpay.console.tipopendenza;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.model.TipoPendenza;
import it.govpay.console.model.TipoPendenzaAvvisaturaAppIo;
import it.govpay.console.model.TipoPendenzaAvvisaturaMail;
import it.govpay.console.model.TipoPendenzaForm;
import it.govpay.console.model.TipoPendenzaFormPagamento;
import it.govpay.console.model.TipoPendenzaPortaleBackoffice;
import it.govpay.console.model.TipoPendenzaPortalePagamento;
import it.govpay.console.model.TipoPendenzaPromemoriaAvvisoAppIo;
import it.govpay.console.model.TipoPendenzaPromemoriaAvvisoMail;
import it.govpay.console.model.TipoPendenzaPromemoriaRicevutaAppIo;
import it.govpay.console.model.TipoPendenzaPromemoriaRicevutaMail;
import it.govpay.console.model.TipoPendenzaPromemoriaScadenza;
import it.govpay.console.model.TipoPendenzaSummary;
import it.govpay.console.model.TipoPendenzaTracciatoCsv;
import it.govpay.console.model.TipoPendenzaTrasformazione;
import it.govpay.console.model.TipoTemplateTrasformazione;
import it.govpay.console.web.BadRequestException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Mappa l'entity {@link TipoVersamento} (tabella flat con ~55 colonne) verso e
 * dalle proiezioni V2 annidate. I campi {@code type: object} dell'API sono
 * persistiti come JSON serializzato su colonne TEXT.
 */
@Component
public class TipoPendenzaMapper {

    private final ObjectMapper objectMapper;

    public TipoPendenzaMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TipoPendenzaSummary toSummary(TipoVersamento e) {
        TipoPendenzaSummary dto = new TipoPendenzaSummary();
        dto.setIdTipoPendenza(e.getCodTipoVersamento());
        dto.setDescrizione(e.getDescrizione());
        dto.setAbilitato(e.getAbilitato());
        return dto;
    }

    // ---- entity -> detail -------------------------------------------------

    public TipoPendenza toDetail(TipoVersamento e) {
        TipoPendenza dto = new TipoPendenza();
        dto.setIdTipoPendenza(e.getCodTipoVersamento());
        dto.setDescrizione(e.getDescrizione());
        dto.setCodificaIUV(e.getCodificaIuv());
        dto.setPagaTerzi(e.getPagaTerzi());
        dto.setAbilitato(e.getAbilitato());
        dto.setPortaleBackoffice(buildPortaleBackoffice(e));
        dto.setPortalePagamento(buildPortalePagamento(e));
        dto.setAvvisaturaMail(buildAvvisaturaMail(e));
        dto.setAvvisaturaAppIO(buildAvvisaturaAppIo(e));
        dto.setVisualizzazione(textToObj(e.getVisualizzazioneDefinizione()));
        dto.setTracciatoCsv(buildTracciatoCsv(e));
        return dto;
    }

    private TipoPendenzaPortaleBackoffice buildPortaleBackoffice(TipoVersamento e) {
        TipoPendenzaPortaleBackoffice bo = new TipoPendenzaPortaleBackoffice();
        bo.setAbilitato(Boolean.TRUE.equals(e.getBoAbilitato()));
        if (e.getBoFormTipo() != null || e.getBoFormDefinizione() != null) {
            TipoPendenzaForm form = new TipoPendenzaForm();
            form.setTipo(e.getBoFormTipo());
            form.setDefinizione(textToObj(e.getBoFormDefinizione()));
            bo.setForm(form);
        }
        bo.setValidazione(textToObj(e.getBoValidazioneDef()));
        if (e.getBoTrasformazioneTipo() != null || e.getBoTrasformazioneDef() != null) {
            TipoPendenzaTrasformazione tr = new TipoPendenzaTrasformazione();
            tr.setTipo(textToEnum(e.getBoTrasformazioneTipo()));
            tr.setDefinizione(textToObj(e.getBoTrasformazioneDef()));
            bo.setTrasformazione(tr);
        }
        bo.setInoltro(e.getBoCodApplicazione());
        return bo;
    }

    private TipoPendenzaPortalePagamento buildPortalePagamento(TipoVersamento e) {
        TipoPendenzaPortalePagamento pag = new TipoPendenzaPortalePagamento();
        pag.setAbilitato(Boolean.TRUE.equals(e.getPagAbilitato()));
        if (e.getPagFormTipo() != null || e.getPagFormDefinizione() != null
                || e.getPagFormImpaginazione() != null) {
            TipoPendenzaFormPagamento form = new TipoPendenzaFormPagamento();
            form.setTipo(e.getPagFormTipo());
            form.setDefinizione(textToObj(e.getPagFormDefinizione()));
            form.setImpaginazione(textToObj(e.getPagFormImpaginazione()));
            pag.setForm(form);
        }
        pag.setValidazione(textToObj(e.getPagValidazioneDef()));
        if (e.getPagTrasformazioneTipo() != null || e.getPagTrasformazioneDef() != null) {
            TipoPendenzaTrasformazione tr = new TipoPendenzaTrasformazione();
            tr.setTipo(textToEnum(e.getPagTrasformazioneTipo()));
            tr.setDefinizione(textToObj(e.getPagTrasformazioneDef()));
            pag.setTrasformazione(tr);
        }
        pag.setInoltro(e.getPagCodApplicazione());
        return pag;
    }

    private TipoPendenzaAvvisaturaMail buildAvvisaturaMail(TipoVersamento e) {
        TipoPendenzaAvvisaturaMail mail = new TipoPendenzaAvvisaturaMail();

        TipoPendenzaPromemoriaAvvisoMail avv = new TipoPendenzaPromemoriaAvvisoMail();
        avv.setAbilitato(Boolean.TRUE.equals(e.getAvvMailPromAvvAbilitato()));
        avv.setTipo(textToEnum(e.getAvvMailPromAvvTipo()));
        avv.setOggetto(textToObj(e.getAvvMailPromAvvOggetto()));
        avv.setMessaggio(textToObj(e.getAvvMailPromAvvMessaggio()));
        avv.setAllegaPdf(e.getAvvMailPromAvvPdf());
        mail.setPromemoriaAvviso(avv);

        TipoPendenzaPromemoriaRicevutaMail ric = new TipoPendenzaPromemoriaRicevutaMail();
        ric.setAbilitato(Boolean.TRUE.equals(e.getAvvMailPromRicAbilitato()));
        ric.setTipo(textToEnum(e.getAvvMailPromRicTipo()));
        ric.setOggetto(textToObj(e.getAvvMailPromRicOggetto()));
        ric.setMessaggio(textToObj(e.getAvvMailPromRicMessaggio()));
        ric.setSoloEseguiti(e.getAvvMailPromRicEseguiti());
        ric.setAllegaPdf(e.getAvvMailPromRicPdf());
        mail.setPromemoriaRicevuta(ric);

        mail.setPromemoriaScadenza(buildScadenza(
                e.getAvvMailPromScadAbilitato(), e.getAvvMailPromScadTipo(),
                e.getAvvMailPromScadOggetto(), e.getAvvMailPromScadMessaggio(),
                e.getAvvMailPromScadPreavviso()));
        return mail;
    }

    private TipoPendenzaAvvisaturaAppIo buildAvvisaturaAppIo(TipoVersamento e) {
        TipoPendenzaAvvisaturaAppIo appio = new TipoPendenzaAvvisaturaAppIo();

        TipoPendenzaPromemoriaAvvisoAppIo avv = new TipoPendenzaPromemoriaAvvisoAppIo();
        avv.setAbilitato(Boolean.TRUE.equals(e.getAvvAppIoPromAvvAbilitato()));
        avv.setTipo(textToEnum(e.getAvvAppIoPromAvvTipo()));
        avv.setOggetto(textToObj(e.getAvvAppIoPromAvvOggetto()));
        avv.setMessaggio(textToObj(e.getAvvAppIoPromAvvMessaggio()));
        appio.setPromemoriaAvviso(avv);

        TipoPendenzaPromemoriaRicevutaAppIo ric = new TipoPendenzaPromemoriaRicevutaAppIo();
        ric.setAbilitato(Boolean.TRUE.equals(e.getAvvAppIoPromRicAbilitato()));
        ric.setTipo(textToEnum(e.getAvvAppIoPromRicTipo()));
        ric.setOggetto(textToObj(e.getAvvAppIoPromRicOggetto()));
        ric.setMessaggio(textToObj(e.getAvvAppIoPromRicMessaggio()));
        ric.setSoloEseguiti(e.getAvvAppIoPromRicEseguiti());
        appio.setPromemoriaRicevuta(ric);

        appio.setPromemoriaScadenza(buildScadenza(
                e.getAvvAppIoPromScadAbilitato(), e.getAvvAppIoPromScadTipo(),
                e.getAvvAppIoPromScadOggetto(), e.getAvvAppIoPromScadMessaggio(),
                e.getAvvAppIoPromScadPreavviso()));
        return appio;
    }

    private TipoPendenzaPromemoriaScadenza buildScadenza(Boolean abilitato, String tipo,
            String oggetto, String messaggio, Integer preavviso) {
        TipoPendenzaPromemoriaScadenza scad = new TipoPendenzaPromemoriaScadenza();
        scad.setAbilitato(Boolean.TRUE.equals(abilitato));
        scad.setTipo(textToEnum(tipo));
        scad.setOggetto(textToObj(oggetto));
        scad.setMessaggio(textToObj(messaggio));
        scad.setPreavviso(preavviso);
        return scad;
    }

    private TipoPendenzaTracciatoCsv buildTracciatoCsv(TipoVersamento e) {
        if (e.getTracCsvTipo() == null && e.getTracCsvHeaderRisposta() == null
                && e.getTracCsvTemplateRichiesta() == null && e.getTracCsvTemplateRisposta() == null) {
            return null;
        }
        TipoPendenzaTracciatoCsv csv = new TipoPendenzaTracciatoCsv();
        csv.setTipo(textToEnum(e.getTracCsvTipo()));
        csv.setIntestazione(e.getTracCsvHeaderRisposta());
        csv.setRichiesta(textToObj(e.getTracCsvTemplateRichiesta()));
        csv.setRisposta(textToObj(e.getTracCsvTemplateRisposta()));
        return csv;
    }

    // ---- writable -> entity ----------------------------------------------

    /**
     * Scrive la parte modificabile sull'entity. Le colonne NOT NULL della
     * tabella ({@code paga_terzi, abilitato, *_abilitato}) ricevono sempre un
     * valore: i flag annidati assenti diventano {@code false}.
     */
    public void applyWritable(TipoVersamento e,
            String descrizione,
            String codificaIUV,
            Boolean pagaTerzi,
            Boolean abilitato,
            TipoPendenzaPortaleBackoffice bo,
            TipoPendenzaPortalePagamento pag,
            TipoPendenzaAvvisaturaMail mail,
            TipoPendenzaAvvisaturaAppIo appio,
            Object visualizzazione,
            TipoPendenzaTracciatoCsv csv) {

        e.setDescrizione(descrizione);
        e.setCodificaIuv(codificaIUV);
        e.setPagaTerzi(pagaTerzi != null ? pagaTerzi : Boolean.FALSE);
        e.setAbilitato(abilitato != null ? abilitato : Boolean.TRUE);
        e.setVisualizzazioneDefinizione(objToText(visualizzazione));

        writePortaleBackoffice(e, bo);
        writePortalePagamento(e, pag);
        writeAvvisaturaMail(e, mail);
        writeAvvisaturaAppIo(e, appio);
        writeTracciatoCsv(e, csv);
    }

    private void writePortaleBackoffice(TipoVersamento e, TipoPendenzaPortaleBackoffice bo) {
        if (bo == null) {
            e.setBoAbilitato(Boolean.FALSE);
            e.setBoFormTipo(null);
            e.setBoFormDefinizione(null);
            e.setBoValidazioneDef(null);
            e.setBoTrasformazioneTipo(null);
            e.setBoTrasformazioneDef(null);
            e.setBoCodApplicazione(null);
            return;
        }
        e.setBoAbilitato(bo.getAbilitato() != null ? bo.getAbilitato() : Boolean.FALSE);
        e.setBoFormTipo(bo.getForm() != null ? bo.getForm().getTipo() : null);
        e.setBoFormDefinizione(bo.getForm() != null ? objToText(bo.getForm().getDefinizione()) : null);
        e.setBoValidazioneDef(objToText(bo.getValidazione()));
        e.setBoTrasformazioneTipo(bo.getTrasformazione() != null ? enumToText(bo.getTrasformazione().getTipo()) : null);
        e.setBoTrasformazioneDef(bo.getTrasformazione() != null ? objToText(bo.getTrasformazione().getDefinizione()) : null);
        e.setBoCodApplicazione(bo.getInoltro());
    }

    private void writePortalePagamento(TipoVersamento e, TipoPendenzaPortalePagamento pag) {
        if (pag == null) {
            e.setPagAbilitato(Boolean.FALSE);
            e.setPagFormTipo(null);
            e.setPagFormDefinizione(null);
            e.setPagFormImpaginazione(null);
            e.setPagValidazioneDef(null);
            e.setPagTrasformazioneTipo(null);
            e.setPagTrasformazioneDef(null);
            e.setPagCodApplicazione(null);
            return;
        }
        e.setPagAbilitato(pag.getAbilitato() != null ? pag.getAbilitato() : Boolean.FALSE);
        e.setPagFormTipo(pag.getForm() != null ? pag.getForm().getTipo() : null);
        e.setPagFormDefinizione(pag.getForm() != null ? objToText(pag.getForm().getDefinizione()) : null);
        e.setPagFormImpaginazione(pag.getForm() != null ? objToText(pag.getForm().getImpaginazione()) : null);
        e.setPagValidazioneDef(objToText(pag.getValidazione()));
        e.setPagTrasformazioneTipo(pag.getTrasformazione() != null ? enumToText(pag.getTrasformazione().getTipo()) : null);
        e.setPagTrasformazioneDef(pag.getTrasformazione() != null ? objToText(pag.getTrasformazione().getDefinizione()) : null);
        e.setPagCodApplicazione(pag.getInoltro());
    }

    private void writeAvvisaturaMail(TipoVersamento e, TipoPendenzaAvvisaturaMail mail) {
        TipoPendenzaPromemoriaAvvisoMail avv = mail != null ? mail.getPromemoriaAvviso() : null;
        e.setAvvMailPromAvvAbilitato(avv != null && Boolean.TRUE.equals(avv.getAbilitato()));
        e.setAvvMailPromAvvPdf(avv != null ? avv.getAllegaPdf() : null);
        e.setAvvMailPromAvvTipo(avv != null ? enumToText(avv.getTipo()) : null);
        e.setAvvMailPromAvvOggetto(avv != null ? objToText(avv.getOggetto()) : null);
        e.setAvvMailPromAvvMessaggio(avv != null ? objToText(avv.getMessaggio()) : null);

        TipoPendenzaPromemoriaRicevutaMail ric = mail != null ? mail.getPromemoriaRicevuta() : null;
        e.setAvvMailPromRicAbilitato(ric != null && Boolean.TRUE.equals(ric.getAbilitato()));
        e.setAvvMailPromRicPdf(ric != null ? ric.getAllegaPdf() : null);
        e.setAvvMailPromRicTipo(ric != null ? enumToText(ric.getTipo()) : null);
        e.setAvvMailPromRicOggetto(ric != null ? objToText(ric.getOggetto()) : null);
        e.setAvvMailPromRicMessaggio(ric != null ? objToText(ric.getMessaggio()) : null);
        e.setAvvMailPromRicEseguiti(ric != null ? ric.getSoloEseguiti() : null);

        TipoPendenzaPromemoriaScadenza scad = mail != null ? mail.getPromemoriaScadenza() : null;
        e.setAvvMailPromScadAbilitato(scad != null && Boolean.TRUE.equals(scad.getAbilitato()));
        e.setAvvMailPromScadPreavviso(scad != null ? scad.getPreavviso() : null);
        e.setAvvMailPromScadTipo(scad != null ? enumToText(scad.getTipo()) : null);
        e.setAvvMailPromScadOggetto(scad != null ? objToText(scad.getOggetto()) : null);
        e.setAvvMailPromScadMessaggio(scad != null ? objToText(scad.getMessaggio()) : null);
    }

    private void writeAvvisaturaAppIo(TipoVersamento e, TipoPendenzaAvvisaturaAppIo appio) {
        TipoPendenzaPromemoriaAvvisoAppIo avv = appio != null ? appio.getPromemoriaAvviso() : null;
        e.setAvvAppIoPromAvvAbilitato(avv != null && Boolean.TRUE.equals(avv.getAbilitato()));
        e.setAvvAppIoPromAvvTipo(avv != null ? enumToText(avv.getTipo()) : null);
        e.setAvvAppIoPromAvvOggetto(avv != null ? objToText(avv.getOggetto()) : null);
        e.setAvvAppIoPromAvvMessaggio(avv != null ? objToText(avv.getMessaggio()) : null);

        TipoPendenzaPromemoriaRicevutaAppIo ric = appio != null ? appio.getPromemoriaRicevuta() : null;
        e.setAvvAppIoPromRicAbilitato(ric != null && Boolean.TRUE.equals(ric.getAbilitato()));
        e.setAvvAppIoPromRicTipo(ric != null ? enumToText(ric.getTipo()) : null);
        e.setAvvAppIoPromRicOggetto(ric != null ? objToText(ric.getOggetto()) : null);
        e.setAvvAppIoPromRicMessaggio(ric != null ? objToText(ric.getMessaggio()) : null);
        e.setAvvAppIoPromRicEseguiti(ric != null ? ric.getSoloEseguiti() : null);

        TipoPendenzaPromemoriaScadenza scad = appio != null ? appio.getPromemoriaScadenza() : null;
        e.setAvvAppIoPromScadAbilitato(scad != null && Boolean.TRUE.equals(scad.getAbilitato()));
        e.setAvvAppIoPromScadPreavviso(scad != null ? scad.getPreavviso() : null);
        e.setAvvAppIoPromScadTipo(scad != null ? enumToText(scad.getTipo()) : null);
        e.setAvvAppIoPromScadOggetto(scad != null ? objToText(scad.getOggetto()) : null);
        e.setAvvAppIoPromScadMessaggio(scad != null ? objToText(scad.getMessaggio()) : null);
    }

    private void writeTracciatoCsv(TipoVersamento e, TipoPendenzaTracciatoCsv csv) {
        if (csv == null) {
            e.setTracCsvTipo(null);
            e.setTracCsvHeaderRisposta(null);
            e.setTracCsvTemplateRichiesta(null);
            e.setTracCsvTemplateRisposta(null);
            return;
        }
        e.setTracCsvTipo(enumToText(csv.getTipo()));
        e.setTracCsvHeaderRisposta(csv.getIntestazione());
        e.setTracCsvTemplateRichiesta(objToText(csv.getRichiesta()));
        e.setTracCsvTemplateRisposta(objToText(csv.getRisposta()));
    }

    // ---- serdes helpers ---------------------------------------------------

    private String objToText(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }

    private Object textToObj(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (JacksonException ex) {
            throw new BadRequestException("Contenuto JSON non valido sul dato persistito: " + ex.getOriginalMessage());
        }
    }

    private static String enumToText(TipoTemplateTrasformazione value) {
        return value == null ? null : value.getValue();
    }

    private static TipoTemplateTrasformazione textToEnum(String value) {
        return value == null ? null : TipoTemplateTrasformazione.fromValue(value);
    }
}
