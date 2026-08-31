package it.govpay.console.tracciato;

import java.util.List;

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
        String raw = StatoOperazioneMapping.toRaw(value);
        return (root, q, cb) -> cb.equal(root.get("stato"), raw);
    }

    public static Specification<Operazione> tipoOperazioneExact(TipoOperazionePendenza value) {
        if (value == null) {
            return null;
        }
        List<String> raw = TipoOperazioneMapping.toRaw(value);
        return (root, q, cb) -> root.get("tipoOperazione").in(raw);
    }
}
