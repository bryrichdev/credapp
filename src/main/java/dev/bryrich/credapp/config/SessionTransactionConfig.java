package dev.bryrich.credapp.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class SessionTransactionConfig {
    /**
     * Sessions must load before authentication can select a workspace. A JPA-backed
     * session transaction would ask for that workspace while still loading the session.
     */
    @Bean
    public TransactionOperations springSessionTransactionOperations(DataSource dataSource) {
        TransactionTemplate template = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
