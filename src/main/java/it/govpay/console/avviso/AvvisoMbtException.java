package it.govpay.console.avviso;

/**
 * Pendenza con Marca da Bollo Telematica: la rifiuta esplicitamente perche'
 * l'avviso di pagamento PDF non e' applicabile a una MBT.
 * Mappata a 422 problem+json.
 */
public class AvvisoMbtException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AvvisoMbtException(String message) {
        super(message);
    }
}
