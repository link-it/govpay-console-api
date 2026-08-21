package it.govpay.console.tracciato;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Operazione;
import it.govpay.console.model.StatoOperazionePendenza;
import it.govpay.console.model.TipoOperazionePendenza;

public final class OperazioneSpecifications {

    private OperazioneSpecifications() {
    }

    public static Specification<Operazione> diTracciato(Long idTracciato) {
        return (root, q, cb) -> cb.equal(root.get("tracciato").get("id"), idTracciato);
    }

    public static Specification<Operazione> statoExact(StatoOperazionePendenza value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("stato"), value.getValue());
    }

    public static Specification<Operazione> tipoOperazioneExact(TipoOperazionePendenza value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("tipoOperazione"), value.getValue());
    }
}
