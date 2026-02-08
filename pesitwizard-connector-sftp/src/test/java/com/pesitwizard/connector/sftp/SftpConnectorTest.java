package com.pesitwizard.connector.sftp;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorException;

class SftpConnectorTest {

    private SftpConnector connector;

    @BeforeEach
    void setUp() {
        connector = new SftpConnector();
    }

    // --- Metadata ---

    @Test
    void getType_returnsSftp() {
        assertThat(connector.getType()).isEqualTo("sftp");
    }

    @Test
    void getName_returnsExpected() {
        assertThat(connector.getName()).isEqualTo("SFTP");
    }

    @Test
    void getVersion_returns100() {
        assertThat(connector.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void supportsResume_true() {
        assertThat(connector.supportsResume()).isTrue();
    }

    // --- Parameter definitions ---

    @Test
    void requiredParameters_containsHostAndUsername() {
        List<ConfigParameter> params = connector.getRequiredParameters();
        assertThat(params).hasSize(2);
        assertThat(params).extracting(ConfigParameter::getName)
                .containsExactly("host", "username");
    }

    @Test
    void optionalParameters_containsExpectedKeys() {
        List<ConfigParameter> params = connector.getOptionalParameters();
        assertThat(params).extracting(ConfigParameter::getName)
                .containsExactly("password", "port", "knownHostsFile", "privateKey", "basePath");
    }

    @Test
    void optionalParameters_passwordIsSensitive() {
        List<ConfigParameter> params = connector.getOptionalParameters();
        ConfigParameter pwd = params.stream().filter(p -> "password".equals(p.getName())).findFirst().orElseThrow();
        assertThat(pwd.isSensitive()).isTrue();
    }

    @Test
    void optionalParameters_portDefaultIs22() {
        List<ConfigParameter> params = connector.getOptionalParameters();
        ConfigParameter port = params.stream().filter(p -> "port".equals(p.getName())).findFirst().orElseThrow();
        assertThat(port.getDefaultValue()).isEqualTo("22");
    }

    // --- Not-initialized guard ---

    @Test
    void testConnection_beforeInit_throws() {
        assertThatThrownBy(() -> connector.testConnection())
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void exists_beforeInit_throws() {
        assertThatThrownBy(() -> connector.exists("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void read_beforeInit_throws() {
        assertThatThrownBy(() -> connector.read("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void write_beforeInit_throws() {
        assertThatThrownBy(() -> connector.write("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void delete_beforeInit_throws() {
        assertThatThrownBy(() -> connector.delete("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void list_beforeInit_throws() {
        assertThatThrownBy(() -> connector.list("/"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void getMetadata_beforeInit_throws() {
        assertThatThrownBy(() -> connector.getMetadata("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void rename_beforeInit_throws() {
        assertThatThrownBy(() -> connector.rename("a", "b"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void mkdir_beforeInit_throws() {
        assertThatThrownBy(() -> connector.mkdir("dir"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    // --- Initialization validation ---

    @Test
    void initialize_missingHost_throws() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("username", "user");

        assertThatThrownBy(() -> connector.initialize(cfg))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Host required");
    }

    @Test
    void initialize_missingUsername_throws() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("host", "example.com");

        assertThatThrownBy(() -> connector.initialize(cfg))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Username required");
    }

    @Test
    void initialize_connectionFailure_throws() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("host", "192.0.2.1"); // RFC 5737 documentation address, guaranteed unreachable
        cfg.put("username", "user");
        cfg.put("password", "pass");
        cfg.put("port", "22");

        // Connection will fail (unreachable host) with CONNECTION_FAILED
        assertThatThrownBy(() -> connector.initialize(cfg))
                .isInstanceOf(ConnectorException.class);
    }

    // --- close ---

    @Test
    void close_beforeInit_doesNotThrow() {
        assertThatCode(() -> connector.close()).doesNotThrowAnyException();
    }
}
