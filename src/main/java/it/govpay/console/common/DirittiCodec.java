package it.govpay.console.common;

import java.util.ArrayList;
import java.util.List;

import it.govpay.console.model.Acl;

/**
 * Codifica/decodifica del campo {@code acl.diritti}, byte-compatibile col core V1.
 *
 * <p>V1 memorizza i diritti come concatenazione <b>senza separatore</b> dei codici
 * {@code R}/{@code W} (es. {@code "R"}, {@code "W"}, {@code "RW"}) e li rilegge in
 * modo tollerante (per contenimento). Qui replichiamo entrambi i lati:
 * <ul>
 *   <li>{@link #parse} tollera qualsiasi stringa che contenga i codici (quindi
 *       anche il formato CSV {@code "R,W"} scritto da versioni precedenti);</li>
 *   <li>{@link #serialize} produce la forma canonica V1 (concatenata, R prima di W).</li>
 * </ul>
 * La tabella {@code acl} e' condivisa col core V1 (autorizzazioni), quindi la
 * compatibilita' byte-a-byte e' importante.
 */
public final class DirittiCodec {

    private DirittiCodec() {
    }

    public static List<Acl.AutorizzazioniEnum> parse(String diritti) {
        List<Acl.AutorizzazioniEnum> out = new ArrayList<>();
        if (diritti == null) {
            return out;
        }
        String upper = diritti.toUpperCase();
        if (upper.contains("R")) {
            out.add(Acl.AutorizzazioniEnum.R);
        }
        if (upper.contains("W")) {
            out.add(Acl.AutorizzazioniEnum.W);
        }
        return out;
    }

    public static String serialize(List<Acl.AutorizzazioniEnum> autorizzazioni) {
        StringBuilder sb = new StringBuilder();
        if (autorizzazioni != null) {
            if (autorizzazioni.contains(Acl.AutorizzazioniEnum.R)) {
                sb.append('R');
            }
            if (autorizzazioni.contains(Acl.AutorizzazioniEnum.W)) {
                sb.append('W');
            }
        }
        return sb.toString();
    }
}
