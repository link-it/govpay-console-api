package it.govpay.console.ricevuta.pagopa;

/**
 * Errore non recuperabile nella conversione XML→JSON di un tracciato RPT/RT
 * (XML malformato rispetto alla versione attesa, o versione SANP non gestita).
 */
public class RptRtConversionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RptRtConversionException(String message) {
		super(message);
	}

	public RptRtConversionException(String message, Throwable cause) {
		super(message, cause);
	}
}
