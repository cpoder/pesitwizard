package com.pesitwizard.server.config;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JPA configuration for multi-tenant cluster schema support.
 *
 * <p>Uses Hibernate's built-in {@code default_schema} property to qualify all table references with
 * the cluster-specific schema. This replaces the previous regex-based {@code StatementInspector}
 * approach which was fragile and required manual maintenance of table name lists.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JpaConfig {

    private final ClusterSchemaConfig schemaConfig;

    /**
     * Set Hibernate's default_schema to the cluster schema when in multi-tenant mode. Hibernate
     * will automatically qualify all table references with this schema.
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return (Map<String, Object> hibernateProperties) -> {
            String schema = schemaConfig.getSchemaName();
            if (!"public".equals(schema)) {
                hibernateProperties.put("hibernate.default_schema", schema);
                log.info("Hibernate default_schema set to '{}' for multi-tenant mode", schema);
            }
        };
    }
}
