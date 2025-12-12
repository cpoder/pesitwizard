package com.vectis.server.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hibernate StatementInspector that rewrites SQL to use the cluster schema.
 * Replaces references to 'partners' and 'virtual_files' tables with
 * schema-qualified names.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterSchemaInterceptor implements StatementInspector {

    private final ClusterSchemaConfig schemaConfig;

    @Override
    public String inspect(String sql) {
        String schema = schemaConfig.getSchemaName();

        // If using default public schema, no rewriting needed
        if ("public".equals(schema)) {
            return sql;
        }

        // Rewrite table references to use cluster schema
        // All pesit-server tables that should be in the cluster schema
        String rewritten = sql
                .replaceAll("\\bpartners\\b", schema + ".partners")
                .replaceAll("\\bvirtual_files\\b", schema + ".virtual_files")
                .replaceAll("\\bcertificates\\b", schema + ".certificates")
                .replaceAll("\\bcertificate_stores\\b", schema + ".certificate_stores")
                .replaceAll("\\btransfer_records\\b", schema + ".transfer_records")
                .replaceAll("\\baudit_events\\b", schema + ".audit_events")
                .replaceAll("\\bpesit_server_config\\b", schema + ".pesit_server_config")
                .replaceAll("\\bapi_keys\\b", schema + ".api_keys")
                .replaceAll("\\bsecret_entries\\b", schema + ".secret_entries")
                .replaceAll("\\bfile_checksums\\b", schema + ".file_checksums");

        if (!rewritten.equals(sql)) {
            log.trace("Rewritten SQL for schema {}: {}", schema, rewritten);
        }

        return rewritten;
    }
}
