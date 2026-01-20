package com.pesitwizard.connector.local;

import static org.assertj.core.api.Assertions.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.FileMetadata;

class LocalFileConnectorTest {

    @TempDir
    Path tempDir;

    private LocalFileConnector connector;

    @BeforeEach
    void setUp() throws Exception {
        connector = new LocalFileConnector();
        connector.initialize(Map.of("basePath", tempDir.toString()));
    }

    @AfterEach
    void tearDown() {
        connector.close();
    }

    @Test
    void testGetType() {
        assertThat(connector.getType()).isEqualTo("local");
    }

    @Test
    void testGetName() {
        assertThat(connector.getName()).isEqualTo("Local Filesystem");
    }

    @Test
    void testTestConnection() throws Exception {
        assertThat(connector.testConnection()).isTrue();
    }

    @Test
    void testWriteAndRead() throws Exception {
        String content = "Hello, Vectis!";

        try (OutputStream os = connector.write("test.txt")) {
            os.write(content.getBytes());
        }

        assertThat(connector.exists("test.txt")).isTrue();

        try (InputStream is = connector.read("test.txt")) {
            String read = new String(is.readAllBytes());
            assertThat(read).isEqualTo(content);
        }
    }

    @Test
    void testReadWithOffset() throws Exception {
        String content = "0123456789";

        try (OutputStream os = connector.write("offset.txt")) {
            os.write(content.getBytes());
        }

        try (InputStream is = connector.read("offset.txt", 5)) {
            String read = new String(is.readAllBytes());
            assertThat(read).isEqualTo("56789");
        }
    }

    @Test
    void testAppendWrite() throws Exception {
        try (OutputStream os = connector.write("append.txt")) {
            os.write("Hello".getBytes());
        }

        try (OutputStream os = connector.write("append.txt", true)) {
            os.write(" World".getBytes());
        }

        try (InputStream is = connector.read("append.txt")) {
            assertThat(new String(is.readAllBytes())).isEqualTo("Hello World");
        }
    }

    @Test
    void testGetMetadata() throws Exception {
        String content = "Test content";
        try (OutputStream os = connector.write("meta.txt")) {
            os.write(content.getBytes());
        }

        FileMetadata meta = connector.getMetadata("meta.txt");
        assertThat(meta.getName()).isEqualTo("meta.txt");
        assertThat(meta.getSize()).isEqualTo(content.length());
        assertThat(meta.isDirectory()).isFalse();
        assertThat(meta.getLastModified()).isNotNull();
    }

    @Test
    void testList() throws Exception {
        connector.mkdir("subdir");
        try (OutputStream os = connector.write("file1.txt")) {
            os.write("content".getBytes());
        }
        try (OutputStream os = connector.write("file2.txt")) {
            os.write("content".getBytes());
        }

        List<FileMetadata> files = connector.list(".");
        assertThat(files).hasSize(3);
        assertThat(files).extracting(FileMetadata::getName)
                .containsExactlyInAnyOrder("subdir", "file1.txt", "file2.txt");
    }

    @Test
    void testMkdir() throws Exception {
        connector.mkdir("newdir/nested");
        assertThat(connector.exists("newdir")).isTrue();
        assertThat(connector.exists("newdir/nested")).isTrue();

        FileMetadata meta = connector.getMetadata("newdir");
        assertThat(meta.isDirectory()).isTrue();
    }

    @Test
    void testDelete() throws Exception {
        try (OutputStream os = connector.write("todelete.txt")) {
            os.write("delete me".getBytes());
        }

        assertThat(connector.exists("todelete.txt")).isTrue();
        connector.delete("todelete.txt");
        assertThat(connector.exists("todelete.txt")).isFalse();
    }

    @Test
    void testRename() throws Exception {
        try (OutputStream os = connector.write("original.txt")) {
            os.write("content".getBytes());
        }

        connector.rename("original.txt", "renamed.txt");

        assertThat(connector.exists("original.txt")).isFalse();
        assertThat(connector.exists("renamed.txt")).isTrue();
    }

    @Test
    void testSupportsResume() {
        assertThat(connector.supportsResume()).isTrue();
    }

    @Test
    void testReadNonExistentFile() {
        assertThatThrownBy(() -> connector.read("nonexistent.txt"))
                .isInstanceOf(ConnectorException.class);
    }

    @Test
    void testPathTraversalPrevention() {
        assertThatThrownBy(() -> connector.read("../../../etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void testNotInitialized() throws Exception {
        try (LocalFileConnector uninit = new LocalFileConnector()) {
            assertThatThrownBy(() -> uninit.exists("test"))
                    .isInstanceOf(ConnectorException.class);
        }
    }
}
