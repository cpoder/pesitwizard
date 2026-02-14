package com.pesitwizard.client.pesit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.connector.ConnectorRegistry;
import com.pesitwizard.client.entity.StorageConnection;
import com.pesitwizard.client.repository.StorageConnectionRepository;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.StorageConnector;
import com.pesitwizard.security.SecretsService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageConnectorFactoryTest {

    @Mock private StorageConnectionRepository connectionRepository;
    @Mock private ConnectorRegistry connectorRegistry;
    @Mock private SecretsService secretsService;

    private StorageConnectorFactory factory;

    @BeforeEach
    void setUp() {
        factory =
                new StorageConnectorFactory(
                        connectionRepository,
                        connectorRegistry,
                        new ObjectMapper(),
                        secretsService);
    }

    @Test
    void createFromConnectionId_notFound_throws() {
        when(connectionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.createFromConnectionId("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createFromConnectionId_disabled_throws() {
        StorageConnection conn = new StorageConnection();
        conn.setName("test");
        conn.setEnabled(false);
        when(connectionRepository.findById("1")).thenReturn(Optional.of(conn));

        assertThatThrownBy(() -> factory.createFromConnectionId("1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void createFromConnection_success() throws ConnectorException {
        StorageConnection conn = new StorageConnection();
        conn.setName("test");
        conn.setEnabled(true);
        conn.setConnectorType("local");
        conn.setConfigJson("{\"basePath\":\"/tmp\"}");

        StorageConnector mockConnector = mock(StorageConnector.class);
        when(connectorRegistry.createConnector(eq("local"), anyMap())).thenReturn(mockConnector);

        StorageConnector result = factory.createFromConnection(conn);
        assertThat(result).isSameAs(mockConnector);
    }

    @Test
    void createFromConnection_decryptsSensitiveFields() throws ConnectorException {
        StorageConnection conn = new StorageConnection();
        conn.setName("sftp");
        conn.setEnabled(true);
        conn.setConnectorType("sftp");
        conn.setConfigJson("{\"host\":\"example.com\",\"password\":\"ENC:xxx\"}");

        StorageConnector mockConnector = mock(StorageConnector.class);
        when(connectorRegistry.createConnector(eq("sftp"), anyMap())).thenReturn(mockConnector);
        when(secretsService.decrypt("ENC:xxx")).thenReturn("secret");

        factory.createFromConnection(conn);

        verify(secretsService).decrypt("ENC:xxx");
    }

    @Test
    void createFromConnection_invalidJson_throws() {
        StorageConnection conn = new StorageConnection();
        conn.setName("bad");
        conn.setEnabled(true);
        conn.setConnectorType("local");
        conn.setConfigJson("not json");

        assertThatThrownBy(() -> factory.createFromConnection(conn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to create connector");
    }
}
