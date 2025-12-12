package com.vectis.server.config;

import java.util.Map;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

/**
 * JPA configuration for multi-tenant cluster schema support.
 */
@Configuration
@RequiredArgsConstructor
public class JpaConfig {

    private final ClusterSchemaInterceptor schemaInterceptor;

    /**
     * Register the schema interceptor with Hibernate.
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return (Map<String, Object> hibernateProperties) -> {
            hibernateProperties.put("hibernate.session_factory.statement_inspector", schemaInterceptor);
        };
    }
}
