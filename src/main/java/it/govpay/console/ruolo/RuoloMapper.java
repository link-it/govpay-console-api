package it.govpay.console.ruolo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.govpay.console.common.DirittiCodec;
import it.govpay.console.model.Acl;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.Ruolo;
import it.govpay.console.model.RuoloSummary;

/**
 * Assembla le rappresentazioni {@code RuoloSummary} / {@code Ruolo} a partire
 * dalle righe {@code acl} di definizione del ruolo ({@code id_utenza IS NULL}).
 * Le entry ACL sono ordinate per servizio per rendere deterministico l'{@code ETag}.
 */
@Component
public class RuoloMapper {

    private static final Map<String, AclServizio> SERVIZIO_LOOKUP = Arrays.stream(AclServizio.values())
            .collect(Collectors.toMap(AclServizio::getValue, e -> e));

    public RuoloSummary toSummary(String idRuolo) {
        RuoloSummary dto = new RuoloSummary();
        dto.setIdRuolo(idRuolo);
        return dto;
    }

    public Ruolo toDetail(String idRuolo, List<it.govpay.console.entity.Acl> rows) {
        Ruolo dto = new Ruolo();
        dto.setIdRuolo(idRuolo);
        dto.setAcl(rows.stream()
                .map(RuoloMapper::toAcl)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(a -> a.getServizio().getValue()))
                .toList());
        return dto;
    }

    private static Acl toAcl(it.govpay.console.entity.Acl entity) {
        AclServizio servizio = SERVIZIO_LOOKUP.get(entity.getServizio());
        if (servizio == null) {
            return null; // servizio fuori dall'enum noto → skip
        }
        Acl acl = new Acl();
        acl.setServizio(servizio);
        acl.setAutorizzazioni(DirittiCodec.parse(entity.getDiritti()));
        // Il campo 'ruolo' e' implicito (= idRuolo) e non viene ripetuto per entry.
        return acl;
    }
}
