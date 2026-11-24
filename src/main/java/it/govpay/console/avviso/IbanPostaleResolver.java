package it.govpay.console.avviso;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Tributo;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.TributoRepository;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * IBAN postale di una pendenza: il bollettino postale e' attivo se la prima voce ha un IBAN
 * postale, di accredito o (in subordine) di appoggio. Le voci portano solo le chiavi piatte
 * degli IBAN e del tributo: per una pendenza a riferimento (senza IBAN sulla voce) l'IBAN e'
 * quello del tributo del dominio.
 *
 * <p>Per una sola pendenza si usa {@link #risolvi}; per piu' pendenze {@link #risolviTutte}
 * legge voci, IBAN e tributi in blocco, con un numero di query indipendente dal numero di
 * pendenze.
 */
@Component
class IbanPostaleResolver {

    private final IbanAccreditoRepository ibanAccreditoRepository;
    private final TributoRepository tributoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    IbanPostaleResolver(IbanAccreditoRepository ibanAccreditoRepository, TributoRepository tributoRepository) {
        this.ibanAccreditoRepository = ibanAccreditoRepository;
        this.tributoRepository = tributoRepository;
    }

    /** L'IBAN postale scelto, o {@code null} se la pendenza non ha bollettino postale. */
    IbanAccredito risolvi(Pendenza pendenza) {
        return scegli(pendenza.getVoci(),
                id -> ibanAccreditoRepository.findById(id).orElse(null),
                id -> tributoRepository.findById(id).orElse(null));
    }

    /** Le voci delle pendenze, per id della pendenza: una sola query. */
    Map<Long, List<VocePendenza>> caricaVoci(Collection<Pendenza> pendenze) {
        if (pendenze.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = pendenze.stream().map(Pendenza::getId).toList();
        return entityManager.createQuery(
                        "select v from VocePendenza v where v.pendenza.id in :ids", VocePendenza.class)
                .setParameter("ids", ids).getResultStream()
                .collect(Collectors.groupingBy(v -> v.getPendenza().getId()));
    }

    /**
     * L'IBAN postale di ciascuna pendenza, per id; le pendenze senza bollettino postale non
     * compaiono. Due query in tutto, oltre a quella delle voci.
     *
     * @param voci le voci di ciascuna pendenza, da {@link #caricaVoci}
     */
    Map<Long, IbanAccredito> risolviTutte(Collection<Pendenza> pendenze, Map<Long, List<VocePendenza>> voci) {
        Set<Long> idIban = new HashSet<>();
        Set<Long> idTributi = new HashSet<>();
        for (Pendenza pendenza : pendenze) {
            VocePendenza prima = prima(voci.get(pendenza.getId()));
            if (prima == null) {
                continue;
            }
            aggiungi(idIban, prima.getIdIbanAccredito());
            aggiungi(idIban, prima.getIdIbanAppoggio());
            aggiungi(idTributi, prima.getIdTributo());
        }
        Map<Long, IbanAccredito> ibanPerId = new HashMap<>();
        if (!idIban.isEmpty()) {
            ibanAccreditoRepository.findAllById(idIban).forEach(i -> ibanPerId.put(i.getId(), i));
        }
        Map<Long, Tributo> tributiPerId = new HashMap<>();
        if (!idTributi.isEmpty()) {
            entityManager.createQuery("select t from Tributo t left join fetch t.ibanAccredito "
                            + "left join fetch t.ibanAppoggio where t.id in :ids", Tributo.class)
                    .setParameter("ids", idTributi).getResultList()
                    .forEach(t -> tributiPerId.put(t.getId(), t));
        }
        Map<Long, IbanAccredito> risultato = new HashMap<>();
        for (Pendenza pendenza : pendenze) {
            IbanAccredito postale = scegli(voci.get(pendenza.getId()), ibanPerId::get, tributiPerId::get);
            if (postale != null) {
                risultato.put(pendenza.getId(), postale);
            }
        }
        return risultato;
    }

    private static IbanAccredito scegli(List<VocePendenza> voci, Function<Long, IbanAccredito> iban,
                                        Function<Long, Tributo> tributo) {
        VocePendenza prima = prima(voci);
        if (prima == null) {
            return null;
        }
        Tributo tributoDellaVoce = prima.getIdTributo() == null ? null : tributo.apply(prima.getIdTributo());

        IbanAccredito accredito = prima.getIdIbanAccredito() != null
                ? iban.apply(prima.getIdIbanAccredito())
                : (tributoDellaVoce != null ? tributoDellaVoce.getIbanAccredito() : null);
        if (isPostale(accredito)) {
            return accredito;
        }
        IbanAccredito appoggio = prima.getIdIbanAppoggio() != null
                ? iban.apply(prima.getIdIbanAppoggio())
                : (tributoDellaVoce != null ? tributoDellaVoce.getIbanAppoggio() : null);
        return isPostale(appoggio) ? appoggio : null;
    }

    /** La voce con indice minore, non la prima della lista. */
    private static VocePendenza prima(List<VocePendenza> voci) {
        if (voci == null || voci.isEmpty()) {
            return null;
        }
        return voci.stream().min(Comparator.comparingInt(VocePendenza::getIndice)).orElseThrow();
    }

    private static void aggiungi(Set<Long> insieme, Long id) {
        if (id != null) {
            insieme.add(id);
        }
    }

    private static boolean isPostale(IbanAccredito iban) {
        return iban != null && Boolean.TRUE.equals(iban.getPostale());
    }
}
