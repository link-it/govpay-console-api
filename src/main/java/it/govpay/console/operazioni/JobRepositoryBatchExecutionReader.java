package it.govpay.console.operazioni;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

@Component
public class JobRepositoryBatchExecutionReader implements BatchExecutionReader {

    private final JobRepository jobRepository;

    public JobRepositoryBatchExecutionReader(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public JobInstance getLastJobInstance(String jobName) {
        return jobRepository.getLastJobInstance(jobName);
    }

    @Override
    public JobExecution getLastJobExecution(JobInstance jobInstance) {
        return jobRepository.getLastJobExecution(jobInstance);
    }
}
