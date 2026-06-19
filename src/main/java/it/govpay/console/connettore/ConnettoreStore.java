package it.govpay.console.connettore;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.ConnettoreProprieta;
import it.govpay.console.repository.ConnettoreProprietaRepository;

/**
 * Lettura/scrittura delle proprieta' EAV di un connettore. La {@link #upsert}
 * gestisce solo le chiavi indicate ({@code managedKeys}): valori presenti vengono
 * inseriti/aggiornati, valori assenti rimuovono la riga; le proprieta' fuori
 * dal set restano intatte (cosi' la PUT di config non tocca le credenziali e
 * viceversa).
 */
@Component
public class ConnettoreStore {

    private final ConnettoreProprietaRepository repository;

    public ConnettoreStore(ConnettoreProprietaRepository repository) {
        this.repository = repository;
    }

    public Map<String, String> read(String codConnettore) {
        Map<String, String> map = new HashMap<>();
        for (ConnettoreProprieta p : repository.findByCodConnettore(codConnettore)) {
            map.put(p.getCodProprieta(), p.getValore());
        }
        return map;
    }

    public void upsert(String codConnettore, Map<String, String> desired, Set<String> managedKeys) {
        Map<String, ConnettoreProprieta> existing = repository.findByCodConnettore(codConnettore).stream()
                .filter(p -> managedKeys.contains(p.getCodProprieta()))
                .collect(Collectors.toMap(ConnettoreProprieta::getCodProprieta, Function.identity()));

        for (String key : managedKeys) {
            String value = desired.get(key);
            ConnettoreProprieta row = existing.get(key);
            if (value != null && !value.isEmpty()) {
                if (row != null) {
                    row.setValore(value);
                    repository.save(row);
                } else {
                    repository.save(new ConnettoreProprieta(codConnettore, key, value));
                }
            } else if (row != null) {
                repository.delete(row);
            }
        }
    }
}
