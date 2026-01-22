package com.pesitwizard.connector.local;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.StorageConnector;

/**
 * Unit tests for LocalFileConnectorFactory.
 */
@DisplayName("LocalFileConnectorFactory Tests")
class LocalFileConnectorFactoryTest {

    private LocalFileConnectorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new LocalFileConnectorFactory();
    }

    @Test
    @DisplayName("should return correct type")
    void shouldReturnCorrectType() {
        assertThat(factory.getType()).isEqualTo("local");
    }

    @Test
    @DisplayName("should return correct name")
    void shouldReturnCorrectName() {
        assertThat(factory.getName()).isEqualTo("Local Filesystem");
    }

    @Test
    @DisplayName("should return version")
    void shouldReturnVersion() {
        assertThat(factory.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("should return description")
    void shouldReturnDescription() {
        assertThat(factory.getDescription()).isEqualTo("Access files on the local filesystem");
    }

    @Test
    @DisplayName("should create LocalFileConnector instance")
    void shouldCreateLocalFileConnectorInstance() {
        StorageConnector connector = factory.create();

        assertThat(connector).isNotNull();
        assertThat(connector).isInstanceOf(LocalFileConnector.class);
    }

    @Test
    @DisplayName("should return empty required parameters")
    void shouldReturnEmptyRequiredParameters() {
        List<ConfigParameter> params = factory.getRequiredParameters();

        assertThat(params).isEmpty();
    }

    @Test
    @DisplayName("should return optional basePath parameter")
    void shouldReturnOptionalBasePathParameter() {
        List<ConfigParameter> params = factory.getOptionalParameters();

        assertThat(params).hasSize(1);
        ConfigParameter basePath = params.get(0);
        assertThat(basePath.getName()).isEqualTo("basePath");
        assertThat(basePath.getDescription()).isEqualTo("Base directory");
        assertThat(basePath.getDefaultValue()).isEqualTo(".");
        assertThat(basePath.getType()).isNotNull();
    }
}
