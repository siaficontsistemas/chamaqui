package com.helpdesk.helpdesk;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

	@Bean(initMethod = "migrate")
	Flyway flyway(DataSource dataSource) {
		return Flyway.configure()
			.dataSource(dataSource)
			.locations("classpath:db/migration")
			.validateMigrationNaming(true)
			.validateOnMigrate(true)
			.load();
	}

	@Bean
	static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlyway() {
		return beanFactory -> {
			if (beanFactory instanceof BeanDefinitionRegistry registry
				&& registry.containsBeanDefinition("entityManagerFactory")
				&& registry.containsBeanDefinition("flyway")) {
				registry.getBeanDefinition("entityManagerFactory").setDependsOn("flyway");
			}
		};
	}

}
