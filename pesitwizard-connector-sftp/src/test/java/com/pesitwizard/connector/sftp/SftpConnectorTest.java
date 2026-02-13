package com.pesitwizard.connector.sftp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.FileMetadata;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        assertThat(params).extracting(ConfigParameter::getName).containsExactly("host", "username");
    }

    @Test
    void optionalParameters_containsExpectedKeys() {
        List<ConfigParameter> params = connector.getOptionalParameters();
        assertThat(params)
                .extracting(ConfigParameter::getName)
                .containsExactly(
                        "password",
                        "port",
                        "knownHostsFile",
                        "privateKey",
                        "basePath",
                        "connectTimeout",
                        "keepaliveInterval",
                        "maxRetries");
    }

    @Test
    void optionalParameters_passwordIsSensitive() {
        List<ConfigParameter> params = connector.getOptionalParameters();
        ConfigParameter pwd =
                params.stream()
                        .filter(p -> "password".equals(p.getName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(pwd.isSensitive()).isTrue();
    }

    @Test
    void optionalParameters_portDefaultIs22() {
        List<ConfigParameter> params = connector.getOptionalParameters();
        ConfigParameter port =
                params.stream().filter(p -> "port".equals(p.getName())).findFirst().orElseThrow();
        assertThat(port.getDefaultValue()).isEqualTo("22");
    }

    @Test
    void optionalParameters_resilienceDefaults() {
        List<ConfigParameter> params = connector.getOptionalParameters();

        ConfigParameter timeout =
                params.stream()
                        .filter(p -> "connectTimeout".equals(p.getName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(timeout.getDefaultValue()).isEqualTo("30000");

        ConfigParameter keepalive =
                params.stream()
                        .filter(p -> "keepaliveInterval".equals(p.getName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(keepalive.getDefaultValue()).isEqualTo("60");

        ConfigParameter retries =
                params.stream()
                        .filter(p -> "maxRetries".equals(p.getName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(retries.getDefaultValue()).isEqualTo("3");
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
        cfg.put("maxRetries", "0"); // Don't retry during test

        // Connection will fail (unreachable host) with CONNECTION_FAILED
        assertThatThrownBy(() -> connector.initialize(cfg)).isInstanceOf(ConnectorException.class);
    }

    // --- close ---

    @Test
    void close_beforeInit_doesNotThrow() {
        assertThatCode(() -> connector.close()).doesNotThrowAnyException();
    }

    // --- Resilience tests with mock channel ---

    @Nested
    @DisplayName("Resilience with Mock Channel")
    class ResilienceTests {

        private ChannelSftp mockChannel;
        private Session mockSession;

        @BeforeEach
        void setUpMocks() throws Exception {
            mockChannel = mock(ChannelSftp.class);
            mockSession = mock(Session.class);
            when(mockSession.isConnected()).thenReturn(true);
            when(mockChannel.isConnected()).thenReturn(true);

            // Set internal state to simulate initialized connector
            setField(connector, "initialized", true);
            setField(connector, "session", mockSession);
            setField(connector, "channel", mockChannel);
            setField(connector, "host", "testhost");
            setField(connector, "port", 22);
            setField(connector, "username", "testuser");
            setField(connector, "basePath", "");
            setField(connector, "maxRetries", 0); // No retries by default
            setField(connector, "keepaliveInterval", 60);
            setField(connector, "connectTimeout", 30000);
        }

        /** Set up JSch mock so that reconnection works in retry tests. */
        private void setUpReconnection() throws Exception {
            JSch mockJsch = mock(JSch.class);
            when(mockJsch.getSession("testuser", "testhost", 22)).thenReturn(mockSession);
            when(mockSession.openChannel("sftp")).thenReturn(mockChannel);
            setField(connector, "jsch", mockJsch);
        }

        @Test
        @DisplayName("exists returns true for existing file")
        void exists_fileExists_returnsTrue() throws Exception {
            SftpATTRS attrs = mock(SftpATTRS.class);
            when(mockChannel.stat("file.txt")).thenReturn(attrs);

            assertThat(connector.exists("file.txt")).isTrue();
        }

        @Test
        @DisplayName("exists returns false for missing file")
        void exists_fileMissing_returnsFalse() throws Exception {
            when(mockChannel.stat("missing.txt"))
                    .thenThrow(new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "not found"));

            assertThat(connector.exists("missing.txt")).isFalse();
        }

        @Test
        @DisplayName("getMetadata returns file metadata")
        void getMetadata_returnsMetadata() throws Exception {
            SftpATTRS attrs = mock(SftpATTRS.class);
            when(attrs.getSize()).thenReturn(1024L);
            when(attrs.getMTime()).thenReturn(1700000000);
            when(attrs.isDir()).thenReturn(false);
            when(mockChannel.stat("file.txt")).thenReturn(attrs);

            FileMetadata meta = connector.getMetadata("file.txt");
            assertThat(meta.getSize()).isEqualTo(1024L);
            assertThat(meta.isDirectory()).isFalse();
        }

        @Test
        @DisplayName("read returns input stream")
        void read_returnsStream() throws Exception {
            ByteArrayInputStream stream = new ByteArrayInputStream("data".getBytes());
            when(mockChannel.get("file.txt")).thenReturn(stream);

            assertThat(connector.read("file.txt")).isSameAs(stream);
        }

        @Test
        @DisplayName("read with offset returns positioned stream")
        void readWithOffset_returnsStream() throws Exception {
            ByteArrayInputStream stream = new ByteArrayInputStream("data".getBytes());
            when(mockChannel.get("file.txt", null, 100L)).thenReturn(stream);

            assertThat(connector.read("file.txt", 100L)).isSameAs(stream);
        }

        @Test
        @DisplayName("write returns output stream")
        void write_returnsStream() throws Exception {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            when(mockChannel.put("file.txt", ChannelSftp.OVERWRITE)).thenReturn(stream);

            assertThat(connector.write("file.txt")).isSameAs(stream);
        }

        @Test
        @DisplayName("write with append uses APPEND mode")
        void writeAppend_usesAppendMode() throws Exception {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            when(mockChannel.put("file.txt", ChannelSftp.APPEND)).thenReturn(stream);

            assertThat(connector.write("file.txt", true)).isSameAs(stream);
        }

        @Test
        @DisplayName("delete calls rm on channel")
        void delete_callsRm() throws Exception {
            connector.delete("file.txt");
            verify(mockChannel).rm("file.txt");
        }

        @Test
        @DisplayName("delete propagates errors (no longer silently swallows)")
        void delete_propagatesErrors() throws Exception {
            when(mockChannel.isConnected()).thenReturn(true);
            doThrow(new SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "denied"))
                    .when(mockChannel)
                    .rm("protected.txt");

            assertThatThrownBy(() -> connector.delete("protected.txt"))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("delete error");
        }

        @Test
        @DisplayName("mkdir creates directory")
        void mkdir_callsMkdir() throws Exception {
            connector.mkdir("newdir");
            verify(mockChannel).mkdir("newdir");
        }

        @Test
        @DisplayName("mkdir tolerates already-existing directory")
        void mkdir_toleratesExisting() throws Exception {
            SftpATTRS attrs = mock(SftpATTRS.class);
            doThrow(new SftpException(ChannelSftp.SSH_FX_FAILURE, "exists"))
                    .when(mockChannel)
                    .mkdir("existdir");
            when(mockChannel.stat("existdir")).thenReturn(attrs);

            // Should not throw — mkdir is idempotent
            assertThatCode(() -> connector.mkdir("existdir")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("mkdir propagates non-exists errors")
        void mkdir_propagatesErrors() throws Exception {
            doThrow(new SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "denied"))
                    .when(mockChannel)
                    .mkdir("forbidden");

            assertThatThrownBy(() -> connector.mkdir("forbidden"))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("mkdir error");
        }

        @Test
        @DisplayName("rename delegates to channel")
        void rename_callsRename() throws Exception {
            connector.rename("old.txt", "new.txt");
            verify(mockChannel).rename("old.txt", "new.txt");
        }

        @Test
        @DisplayName("list returns file metadata list")
        @SuppressWarnings("unchecked")
        void list_returnsFiles() throws Exception {
            Vector<ChannelSftp.LsEntry> entries = new Vector<>();
            ChannelSftp.LsEntry entry = mock(ChannelSftp.LsEntry.class);
            SftpATTRS entryAttrs = mock(SftpATTRS.class);
            when(entry.getFilename()).thenReturn("data.csv");
            when(entry.getAttrs()).thenReturn(entryAttrs);
            when(entryAttrs.getSize()).thenReturn(256L);
            when(entryAttrs.isDir()).thenReturn(false);
            entries.add(entry);
            when(mockChannel.ls("/home")).thenReturn(entries);

            List<FileMetadata> result = connector.list("/home");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("data.csv");
        }

        @Test
        @DisplayName("list filters hidden files")
        @SuppressWarnings("unchecked")
        void list_filtersHiddenFiles() throws Exception {
            Vector<ChannelSftp.LsEntry> entries = new Vector<>();
            ChannelSftp.LsEntry hidden = mock(ChannelSftp.LsEntry.class);
            when(hidden.getFilename()).thenReturn(".hidden");
            ChannelSftp.LsEntry visible = mock(ChannelSftp.LsEntry.class);
            SftpATTRS visAttrs = mock(SftpATTRS.class);
            when(visible.getFilename()).thenReturn("visible.txt");
            when(visible.getAttrs()).thenReturn(visAttrs);
            when(visAttrs.getSize()).thenReturn(10L);
            when(visAttrs.isDir()).thenReturn(false);
            entries.add(hidden);
            entries.add(visible);
            when(mockChannel.ls("/dir")).thenReturn(entries);

            List<FileMetadata> result = connector.list("/dir");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("visible.txt");
        }

        @Test
        @DisplayName("testConnection returns true when connected")
        void testConnection_whenConnected_returnsTrue() throws Exception {
            when(mockChannel.pwd()).thenReturn("/home/testuser");

            assertThat(connector.testConnection()).isTrue();
        }

        @Test
        @DisplayName("testConnection returns false when channel fails")
        void testConnection_whenFails_returnsFalse() throws Exception {
            when(mockChannel.pwd())
                    .thenThrow(new SftpException(ChannelSftp.SSH_FX_FAILURE, "fail"));

            assertThat(connector.testConnection()).isFalse();
        }

        @Test
        @DisplayName("retry on transient failure then succeed")
        void retryOnTransientFailure() throws Exception {
            setField(connector, "maxRetries", 2);
            setUpReconnection();

            SftpATTRS attrs = mock(SftpATTRS.class);
            // First call fails with transient error, second succeeds
            when(mockChannel.stat("file.txt"))
                    .thenThrow(new SftpException(ChannelSftp.SSH_FX_FAILURE, "channel closed"))
                    .thenReturn(attrs);

            assertThat(connector.exists("file.txt")).isTrue();
            verify(mockChannel, times(2)).stat("file.txt");
        }

        @Test
        @DisplayName("no retry on permanent error (FILE_NOT_FOUND)")
        void noRetryOnPermanentError() throws Exception {
            when(mockChannel.stat("missing.txt"))
                    .thenThrow(new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "not found"));

            // Should return false immediately without retry
            assertThat(connector.exists("missing.txt")).isFalse();
            verify(mockChannel, times(1)).stat("missing.txt");
        }

        @Test
        @DisplayName("no retry on permission denied")
        void noRetryOnPermissionDenied() throws Exception {
            when(mockChannel.get("secret.txt"))
                    .thenThrow(new SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "denied"));

            assertThatThrownBy(() -> connector.read("secret.txt"))
                    .isInstanceOf(ConnectorException.class)
                    .satisfies(
                            e ->
                                    assertThat(((ConnectorException) e).getErrorCode())
                                            .isEqualTo(
                                                    ConnectorException.ErrorCode
                                                            .PERMISSION_DENIED));
            verify(mockChannel, times(1)).get("secret.txt");
        }

        @Test
        @DisplayName("exhausted retries throw last exception")
        void exhaustedRetries_throwsLastException() throws Exception {
            setField(connector, "maxRetries", 2);
            setUpReconnection();

            when(mockChannel.stat("file.txt"))
                    .thenThrow(new SftpException(ChannelSftp.SSH_FX_FAILURE, "channel closed"));
            // ensureConnected sees session/channel as connected (mock returns true)
            // but operation always fails → should exhaust retries

            assertThatThrownBy(() -> connector.getMetadata("file.txt"))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("getMetadata failed");
        }

        @Test
        @DisplayName("path traversal rejected")
        void pathTraversal_rejected() {
            assertThatThrownBy(() -> connector.read("../../etc/passwd"))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("traversal");
        }

        @Test
        @DisplayName("null path rejected")
        void nullPath_rejected() {
            assertThatThrownBy(() -> connector.read(null))
                    .isInstanceOf(ConnectorException.class)
                    .hasMessageContaining("traversal");
        }

        @Test
        @DisplayName("close disconnects session and channel")
        void close_disconnectsAll() {
            connector.close();
            verify(mockChannel).disconnect();
            verify(mockSession).disconnect();
        }

        private void setField(Object target, String fieldName, Object value) throws Exception {
            Field f = SftpConnector.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        }
    }
}
