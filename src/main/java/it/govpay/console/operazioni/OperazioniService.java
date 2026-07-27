package it.govpay.console.operazioni;

import java.util.List;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.stereotype.Service;

import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.model.Operazione;

@Service
public class OperazioniService {

    private final OperazioniProperties operazioniProperties;
    private final BatchExecutionReader batchExecutionReader;
    private final OperazioneMapper operazioneMapper;

    public OperazioniService(OperazioniProperties operazioniProperties, BatchExecutionReader batchExecutionReader,
            OperazioneMapper operazioneMapper) {
        this.operazioniProperties = operazioniProperties;
        this.batchExecutionReader = batchExecutionReader;
        this.operazioneMapper = operazioneMapper;
    }

    public List<Operazione> getCatalogo() {
        return operazioniProperties.getCatalogo().stream()
                .map(config -> operazioneMapper.toOperazione(config, ultimaEsecuzioneOf(config)))
                .toList();
    }

    private JobExecution ultimaEsecuzioneOf(OperazioneConfig config) {
        if (config.getJobName() == null) {
            return null;
        }
        JobInstance jobInstance = batchExecutionReader.getLastJobInstance(config.getJobName());
        return jobInstance != null ? batchExecutionReader.getLastJobExecution(jobInstance) : null;
    }
}
