package it.govpay.console.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Riga di {@code batch_job_execution}, tabella standard di Spring Batch
 * (schema condiviso con i microservizi batch). Sola lettura: scritta
 * esclusivamente dal batch proprietario del job via JobRepository.
 */
@Entity
@Table(name = "batch_job_execution")
public class BatchJobExecutionEntity {

    @Id
    @Column(name = "job_execution_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_instance_id", nullable = false)
    private BatchJobInstanceEntity jobInstance;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", length = 10)
    private String status;

    @Column(name = "exit_message", length = 2500)
    private String exitMessage;

    // version/exit_code/last_updated non usati dal codice applicativo, ma
    // mappati per completezza: con ddl-auto=create-drop (test) Hibernate
    // genera lo schema di questa tabella dalla entity, che deve percio'
    // combaciare con le colonne reali su cui scrivono i JdbcDao di Spring
    // Batch (INSERT su tutte le colonne).
    @Column(name = "version")
    private Long version;

    @Column(name = "exit_code", length = 2500)
    private String exitCode;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BatchJobInstanceEntity getJobInstance() {
        return jobInstance;
    }

    public void setJobInstance(BatchJobInstanceEntity jobInstance) {
        this.jobInstance = jobInstance;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExitMessage() {
        return exitMessage;
    }

    public void setExitMessage(String exitMessage) {
        this.exitMessage = exitMessage;
    }
}
