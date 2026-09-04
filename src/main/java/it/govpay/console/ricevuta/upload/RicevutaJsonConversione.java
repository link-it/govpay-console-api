package it.govpay.console.ricevuta.upload;

import it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request;

/**
 * Esito di {@link RicevutaJsonConverter#convert(byte[])}: la richiesta pronta
 * per {@link PaForNodeClient} insieme all'id tecnico del {@code Dominio} gia'
 * risolto durante la conversione — evita una seconda query in
 * {@link RicevutaUploadService} per il check di visibilita' ACL (§E, dopo
 * §D che lo risolve gia').
 */
public record RicevutaJsonConversione(PaSendRTV2Request request, Long idDominio) {
}
