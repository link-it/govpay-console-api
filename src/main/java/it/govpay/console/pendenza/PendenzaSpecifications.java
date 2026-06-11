package it.govpay.console.pendenza;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import it.govpay.console.entity.Versamento;
import it.govpay.console.security.OperatoreCorrente;
import jakarta.persistence.criteria.Predicate;

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
     * Limita i risultati alle pendenze visibili all'operatore corrente, applicando
     * 3 livelli di ACL come fa V1:
     * <ul>
     *   <li><b>dominio/UO</b>: dominio "intero" (utenze_domini.id_uo IS NULL) OR
     *       UO specifica visibile;</li>
     *   <li><b>tipoVersamento</b>: incluso solo se nei tipi autorizzati.</li>
     * </ul>
     * Lista vuota di tipi/domini → predicato falso (lista vuota, mai 403).
     * {@code tuttiIDomini=true} e {@code tuttiITipiVersamento=true} → nessun filtro.
     */
    public static Specification<Versamento> visibiliPerOperatore(OperatoreCorrente operatore) {
        return (root, q, cb) -> {
            Predicate p = cb.conjunction();

            if (!operatore.tuttiIDomini()) {
                Set<Long> dominiInteri = operatore.idDominiInteri();
                Set<Long> uoVisibili = operatore.idUoVisibili();
                if ((dominiInteri == null || dominiInteri.isEmpty())
                        && (uoVisibili == null || uoVisibili.isEmpty())) {
                    return cb.disjunction();
                }
                Predicate viaDominio = (dominiInteri == null || dominiInteri.isEmpty())
                        ? cb.disjunction()
                        : root.get("dominio").get("id").in(dominiInteri);
                Predicate viaUo = (uoVisibili == null || uoVisibili.isEmpty())
                        ? cb.disjunction()
                        : root.get("unitaOperativa").get("id").in(uoVisibili);
                p = cb.and(p, cb.or(viaDominio, viaUo));
            }

            if (!operatore.tuttiITipiVersamento()) {
                Set<Long> tipi = operatore.idTipiVersamentoVisibili();
                if (tipi == null || tipi.isEmpty()) {
                    return cb.disjunction();
                }
                p = cb.and(p, root.get("tipoVersamento").get("id").in(tipi));
            }

            return p;
        };
    }
}
