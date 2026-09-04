package it.govpay.console.ricevuta.upload;

/**
 * Fallimento di trasporto/protocollo verso {@code api-pagopa} (paForNode):
 * circuito aperto, timeout, IO, un fault SOAP a livello di protocollo, oppure
 * un fault applicativo in-band {@code PAA_RECEIPT_DUPLICATA} (possibile esito
 * di un tentativo precedente riuscito la cui risposta e' andata persa). Gli
 * altri fault applicativi in-band restano un rifiuto della RT, mappato
 * altrove a 422 — vedi {@link PaForNodeClient}.
 *
 * <p>Non e' un esito definitivo: chi la cattura (l'orchestratore
 * dell'upload) deve prima rileggere lo stato della ricevuta sul dominio,
 * perche' il Nodo puo' aver comunque acquisito la RT nonostante la risposta
 * non sia arrivata al chiamante. Solo se la rilettura conferma che la RT
 * non e' stata acquisita questo fallimento diventa un 502/504 verso il client.
 */
public class PaForNodeTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean timeout;

    public PaForNodeTransportException(String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
