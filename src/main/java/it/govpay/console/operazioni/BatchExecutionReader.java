package it.govpay.console.operazioni;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;

/**
 * Accesso in sola lettura ai dati di esecuzione batch, ristretto ai soli
 * metodi usati dal catalogo Operazioni: blinda l'intento "sola lettura" a
 * livello di codice, anche se {@code JobRepository} espone anche metodi di
 * scrittura.
 */
public interface BatchExecutionReader {

    JobInstance getLastJobInstance(String jobName);

    JobExecution getLastJobExecution(JobInstance jobInstance);
}
