package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Riga di {@code batch_job_instance}, tabella standard di Spring Batch
 * (schema condiviso con i microservizi batch). Sola lettura: scritta
 * esclusivamente dal batch proprietario del job via JobRepository.
 */
@Entity
@Table(name = "batch_job_instance")
public class BatchJobInstanceEntity {

    @Id
    @Column(name = "job_instance_id")
    private Long id;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    // job_key/version non usati dal codice applicativo, ma mappati per
    // completezza: con ddl-auto=create-drop (test) Hibernate genera lo
    // schema di questa tabella dalla entity, che deve percio' combaciare
    // con le colonne reali su cui scrivono i JdbcDao di Spring Batch.
    @Column(name = "job_key", nullable = false, length = 32)
    private String jobKey;

    @Column(name = "version")
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }
}
