package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Documento: raggruppatore di N versamenti per avvisi multi-rata. In V2 (slim)
 * mappiamo solo {@code id} e {@code codDocumento}: serve a costruire il filename
 * del PDF dell'avviso allineato a V1
 * ({@code {codDominio}_DOC_{codDocumento}.pdf} quando la pendenza appartiene
 * a un documento).
 *
 * <p>L'endpoint dedicato al PDF aggregato del documento intero
 * ({@code /documenti/.../avviso} di V1) e' fuori scope dell'issue #9 V2:
 * verra' mappato in una issue dedicata.
 */
@Entity
@Table(name = "documenti")
@SequenceGenerator(name = "seq_documenti", sequenceName = "seq_documenti", allocationSize = 1)
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_documenti")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_documento", nullable = false, length = 35)
    private String codDocumento;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodDocumento() {
        return codDocumento;
    }

    public void setCodDocumento(String codDocumento) {
        this.codDocumento = codDocumento;
    }
}
