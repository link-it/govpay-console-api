package it.govpay.console.pendenza;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Risolve in blocco le anagrafiche a cui una pendenza del modello v3 punta con chiavi
 * piatte (dominio, tipo pendenza, unita' operativa, tributi, IBAN): una query per
 * anagrafica, indipendente dal numero di voci.
 */
@Component
public class PendenzaAnagrafiche {

    /** Anagrafiche dei tre assi della pendenza; i valori assenti sono {@code null}. */
    public record Contesto(Dominio dominio, TipoVersamento tipoVersamento, UnitaOperativa unitaOperativa) {
    }

    /**
     * Anagrafiche di una pagina di pendenze, per chiave: una query per anagrafica,
     * indipendente dal numero di righe.
     *
     * @param tipi         tipi pendenza, per chiave del tipo del dominio ({@code tipi_vers_domini.id})
     * @param applicazioni codici applicazione (idA2A), per chiave
     */
    public record Pagina(Map<Long, Dominio> domini, Map<Long, TipoVersamento> tipi,
                  Map<Long, UnitaOperativa> unitaOperative, Map<Long, String> applicazioni) {

        public Contesto contesto(Pendenza pendenza) {
            Long idUo = pendenza.getOpzionePagamento().getPosizioneDebitoria().getIdUnitaOperativa();
            return new Contesto(domini.get(pendenza.getIdDominio()), tipi.get(pendenza.getIdTipoPendenza()),
                    unitaOperative.get(idUo));
        }

        public String idA2A(Pendenza pendenza) {
            return applicazioni.get(pendenza.getIdApplicazione());
        }
    }

    /** Anagrafiche delle voci, per chiave. */
    record Voci(Map<Long, Dominio> domini, Map<Long, String> codiciEntrata, Map<Long, String> codiciIban) {
    }

    private final ConsoleDominioRepository dominioRepository;
    private final UnitaOperativaRepository unitaOperativaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    PendenzaAnagrafiche(ConsoleDominioRepository dominioRepository,
                        UnitaOperativaRepository unitaOperativaRepository) {
        this.dominioRepository = dominioRepository;
        this.unitaOperativaRepository = unitaOperativaRepository;
    }

    /**
     * @param idDominio       dominio della pendenza
     * @param idTipoPendenza  chiave del tipo pendenza del dominio ({@code tipi_vers_domini.id})
     * @param idUnitaOperativa unita' operativa della posizione, o {@code null}
     */
    Contesto risolviContesto(Long idDominio, Long idTipoPendenza, Long idUnitaOperativa) {
        Dominio dominio = idDominio == null ? null : dominioRepository.findById(idDominio).orElse(null);
        TipoVersamento tipo = idTipoPendenza == null ? null
                : entityManager.createQuery(
                                "select tvd.tipoVersamento from TipoVersamentoDominio tvd where tvd.id = :id",
                                TipoVersamento.class)
                        .setParameter("id", idTipoPendenza)
                        .getResultStream().findFirst().orElse(null);
        UnitaOperativa uo = idUnitaOperativa == null ? null
                : unitaOperativaRepository.findById(idUnitaOperativa).orElse(null);
        return new Contesto(dominio, tipo, uo);
    }

    /** Risolve le anagrafiche di una pagina di pendenze con opzione e posizione gia' caricate. */
    public Pagina risolviPagina(Collection<Pendenza> pendenze) {
        Set<Long> idDomini = new HashSet<>();
        Set<Long> idTipi = new HashSet<>();
        Set<Long> idUo = new HashSet<>();
        Set<Long> idApplicazioni = new HashSet<>();
        for (Pendenza pendenza : pendenze) {
            add(idDomini, pendenza.getIdDominio());
            add(idTipi, pendenza.getIdTipoPendenza());
            add(idApplicazioni, pendenza.getIdApplicazione());
            add(idUo, pendenza.getOpzionePagamento().getPosizioneDebitoria().getIdUnitaOperativa());
        }

        Map<Long, Dominio> domini = new HashMap<>();
        if (!idDomini.isEmpty()) {
            dominioRepository.findAllById(idDomini).forEach(d -> domini.put(d.getId(), d));
        }
        Map<Long, UnitaOperativa> unitaOperative = new HashMap<>();
        if (!idUo.isEmpty()) {
            unitaOperativaRepository.findAllById(idUo).forEach(u -> unitaOperative.put(u.getId(), u));
        }
        Map<Long, TipoVersamento> tipi = new HashMap<>();
        if (!idTipi.isEmpty()) {
            for (Object[] riga : entityManager.createQuery(
                            "select tvd.id, tv from TipoVersamentoDominio tvd join tvd.tipoVersamento tv "
                                    + "where tvd.id in :ids", Object[].class)
                    .setParameter("ids", idTipi).getResultList()) {
                tipi.put((Long) riga[0], (TipoVersamento) riga[1]);
            }
        }
        Map<Long, String> applicazioni = new HashMap<>(
                codici("select a.id, a.codApplicazione from Applicazione a where a.id in :ids", idApplicazioni));
        return new Pagina(domini, tipi, unitaOperative, applicazioni);
    }

    Voci risolviVoci(List<VocePendenza> voci) {
        Set<Long> idDomini = new HashSet<>();
        Set<Long> idTributi = new HashSet<>();
        Set<Long> idIban = new HashSet<>();
        for (VocePendenza voce : voci) {
            add(idDomini, voce.getIdDominio());
            add(idTributi, voce.getIdTributo());
            add(idIban, voce.getIdIbanAccredito());
            add(idIban, voce.getIdIbanAppoggio());
        }

        Map<Long, Dominio> domini = idDomini.isEmpty() ? Map.of()
                : dominioRepository.findAllById(idDomini).stream()
                        .collect(Collectors.toMap(Dominio::getId, d -> d));
        return new Voci(domini,
                codici("select t.id, tt.codTributo from Tributo t join t.tipoTributo tt where t.id in :ids", idTributi),
                codici("select i.id, i.codIban from IbanAccredito i where i.id in :ids", idIban));
    }

    private Map<Long, String> codici(String jpql, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> risultato = new HashMap<>();
        for (Object[] riga : entityManager.createQuery(jpql, Object[].class)
                .setParameter("ids", ids).getResultList()) {
            risultato.put((Long) riga[0], (String) riga[1]);
        }
        return risultato;
    }

    private static void add(Set<Long> target, Long value) {
        if (value != null) {
            target.add(value);
        }
    }
}
