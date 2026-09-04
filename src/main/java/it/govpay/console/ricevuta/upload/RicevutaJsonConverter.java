package it.govpay.console.ricevuta.upload;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Stazione;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.ricevuta.upload.bizevents.model.CtReceiptModelResponse;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.UnprocessableEntityException;

/**
 * Orchestratore del ramo JSON del caricamento ricevuta:
 * deserializza, valida, risolve {@code Dominio → Stazione → Intermediario} e
 * converte in {@link PaSendRTV2Request} tramite {@link CtReceiptV2Converter}.
 */
@Component
public class RicevutaJsonConverter {

    private final ObjectMapper objectMapper;
    private final RicevutaJsonValidator validator;
    private final DominioRepository dominioRepository;

    public RicevutaJsonConverter(ObjectMapper objectMapper, RicevutaJsonValidator validator,
            DominioRepository dominioRepository) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.dominioRepository = dominioRepository;
    }

    public RicevutaJsonConversione convert(byte[] json) {
        CtReceiptModelResponse response = deserialize(json);
        validator.valida(response);

        String codDominio = response.getFiscalCode();
        Dominio dominio = dominioRepository.findByCodDominio(codDominio)
                .orElseThrow(() -> new BadRequestException(
                        "Dominio '" + codDominio + "' (da 'fiscalCode') non censito in GovPay."));
        Stazione stazione = dominio.getStazione();
        if (stazione == null) {
            throw new BadRequestException("Il dominio '" + codDominio + "' non ha una stazione associata: "
                    + "impossibile risolvere l'intermediario per l'invio della ricevuta.");
        }
        String codIntermediario = stazione.getIntermediario().getCodIntermediario();

        PaSendRTV2Request request = CtReceiptV2Converter.toPaSendRTV2Request(
                codIntermediario, stazione.getCodStazione(), codDominio, response);

        if (CtReceiptV2Converter.hasTransferSenzaIbanEMbdAttachment(request.getReceipt())) {
            throw new UnprocessableEntityException(
                    "La ricevuta contiene una voce di marca da bollo priva dell'allegato (mbdAttachment): "
                            + "pagoPA non lo ha restituito (bug noto, link-it/govpay#843). Il caricamento non e' "
                            + "possibile finche' il bug non e' risolto.");
        }

        return new RicevutaJsonConversione(request, dominio.getId());
    }

    private CtReceiptModelResponse deserialize(byte[] json) {
        CtReceiptModelResponse response;
        try {
            response = objectMapper.readValue(json, CtReceiptModelResponse.class);
        } catch (JacksonException e) {
            throw new UnprocessableEntityException(
                    "Formato RT non supportato: il JSON caricato non e' conforme allo schema "
                            + "CtReceiptModelResponse (BizEvents) atteso.", e);
        }
        // Un body letterale "null" e' JSON sintatticamente valido: Jackson lo deserializza
        // a null senza sollevare eccezioni, non e' intercettato dal catch sopra.
        if (response == null) {
            throw new UnprocessableEntityException(
                    "Formato RT non supportato: il JSON caricato e' 'null', atteso un oggetto "
                            + "CtReceiptModelResponse (BizEvents).");
        }
        return response;
    }
}
