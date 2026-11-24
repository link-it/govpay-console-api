package it.govpay.console.entity;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Unita' operativa storica di una pendenza ({@code versamenti.id_uo}), in sola lettura.
 *
 * <p>La pendenza della libreria non mappa questa colonna: nel modello v3 la unita' operativa sta
 * sulla posizione debitoria. Una pendenza senza opzione di pagamento non ha posizione, e la sua
 * unita' operativa resta solo qui.
 */
@Entity
@Immutable
@Table(name = "versamenti")
public class PendenzaUoStorica {

    /**
     * La riga non si scrive mai da qui. La stessa tabella e' mappata dalla pendenza della libreria,
     * che genera la chiave con {@code IDENTITY}: la dichiarazione uguale mantiene coerente la
     * definizione della colonna.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "id_uo")
    private Long idUo;

    public Long getId() {
        return id;
    }

    public Long getIdUo() {
        return idUo;
    }
}
