package it.govpay.console.unitaoperativa;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.security.OperatoreCorrente;

public final class UnitaOperativaSpecifications {

    private UnitaOperativaSpecifications() {
    }

    public static Specification<UnitaOperativa> byDominioId(Long idDominio) {
        return (root, q, cb) -> cb.equal(root.get("dominio").get("id"), idDominio);
    }

    /** Esclude l'unita' operativa speciale che porta l'anagrafica del dominio. */
    public static Specification<UnitaOperativa> excludeEc(String codUoEc) {
        return (root, q, cb) -> cb.notEqual(root.get("codUo"), codUoEc);
    }

    public static Specification<UnitaOperativa> ragioneSocialePartial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("uoDenominazione")), pattern);
    }

    public static Specification<UnitaOperativa> abilitatoExact(Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("abilitato"), value);
    }

    /**
     * Restrizione a grana UO: un operatore con accesso "intero" al dominio
     * (o {@code tuttiIDomini}) non ha alcuna restrizione aggiuntiva qui — vede
     * tutte le UO del dominio, filtrate solo dagli altri criteri. Un operatore
     * con solo grant parziali sul dominio vede esclusivamente le UO in
     * {@code idUoVisibili}: un insieme parziale vuoto produce nessun risultato,
     * non una scan completa.
     */
    public static Specification<UnitaOperativa> visibiliPerOperatore(Long idDominio, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini() || operatore.idDominiInteri().contains(idDominio)) {
            return null;
        }
        Set<Long> idUoVisibili = operatore.idUoVisibili();
        return (root, q, cb) -> idUoVisibili.isEmpty() ? cb.disjunction() : root.get("id").in(idUoVisibili);
    }
}
