package it.govpay.console.config;

import javax.sql.DataSource;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Espone un {@link JobRepository} sulle tabelle {@code BATCH_*} standard di
 * Spring Batch, condivise nello stesso database fisico con i microservizi
 * batch che le popolano. Nessun job/step e' definito qui: il bean serve solo
 * a leggere lo stato delle esecuzioni scritte altrove (endpoint /operazioni).
 */
@Configuration
public class BatchRepositoryConfig {

    @Bean
    public JobRepository jobRepository(DataSource dataSource, PlatformTransactionManager transactionManager)
            throws Exception {
        JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionManager(transactionManager);
        factory.afterPropertiesSet();
        return factory.getObject();
    }
}
