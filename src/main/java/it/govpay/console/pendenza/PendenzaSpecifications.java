package it.govpay.console.pendenza;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Versamento;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.VersamentoVisibilita;

public final class PendenzaSpecifications {

    private PendenzaSpecifications() {
    }

    public static Specification<Versamento> idPendenzaPartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("codVersamentoEnte")), pattern);
    }

    public static Specification<Versamento> numeroAvvisoExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("numeroAvviso"), value);
    }

    public static Specification<Versamento> idDominioExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("dominio").get("codDominio"), value);
    }

    public static Specification<Versamento> identificativoDebitoreExact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("srcDebitoreIdentificativo"), value);
    }

    /**
     * Limita i risultati alle pendenze visibili all'operatore corrente. Delega la
     * regola ACL alla single-source {@link VersamentoVisibilita}.
     */
    public static Specification<Versamento> visibiliPerOperatore(OperatoreCorrente operatore) {
        return (root, q, cb) -> VersamentoVisibilita.predicate(cb, root, operatore);
    }
}
