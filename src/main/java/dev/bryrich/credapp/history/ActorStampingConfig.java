package dev.bryrich.credapp.history;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class ActorStampingConfig {

    /** Wraps the app's connection pool so every connection says who is signed in. */
    @Bean
    static BeanPostProcessor actorStampingDataSource() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource dataSource && !(bean instanceof ActorStampingDataSource)
                        ? new ActorStampingDataSource(dataSource) : bean;
            }
        };
    }
}
